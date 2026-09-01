package com.solo.onvif.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.media.MediaUrlBuilder;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.service.MediaServerSelector;
import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.entity.OnvifSession;
import com.solo.onvif.mapper.OnvifDeviceMapper;
import com.solo.onvif.request.OnvifPlayStartRequest;
import com.solo.onvif.request.OnvifPlayStopRequest;
import com.solo.onvif.response.OnvifPlayResultResponse;
import com.solo.onvif.service.OnvifPlayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ONVIF 实时点播服务实现
 *
 * <h3>流程（start）</h3>
 * <ol>
 *   <li>校验设备已探测出 stream_uri</li>
 *   <li>幂等：Redis 中已存在同 streamId → 直接返回原播放 URL</li>
 *   <li>拼装带账密的 RTSP URL（URLEncoder 转账密特殊字符）</li>
 *   <li>{@link ZlmRestClient#addStreamProxy} 让 ZLM 拉流，rtp_type=1（TCP）</li>
 *   <li>写 Redis 记账 + 24h TTL 兜底（防 stop / hook 双双漏调时的僵尸）</li>
 *   <li>拼装 flv/wsFlv/hls/rtc 播放地址返回</li>
 * </ol>
 *
 * <p>与国标 {@code GbPlayServiceImpl} 相比省掉了 SIP INVITE、SSRC 分配、on_stream_changed 等待、
 * 超时清扫等一大堆流程，因为 ZLM 自己负责与源建立 RTSP 连接。</p>
 *
 * <h3>生命周期清理</h3>
 * <p>Redis 记账（Key {@code onvif:play:<streamId>}）有三道清理路径，<b>缺一不可</b>：</p>
 * <ol>
 *   <li><b>用户主动 stop</b> → {@link #stop} 显式删 key + 调 {@code delStreamProxy}（正常路径）</li>
 *   <li><b>无人观看</b> → ZLM {@code on_stream_none_reader} 钩子（{@link com.solo.media.hook.ZlmHookService#onStreamNoneReader}）
 *       检测到 ONVIF 流时回 {@code close: true}，ZLM 内部关流；{@code on_stream_changed(regist=false)}
 *       再调 {@link #cleanupByStreamId} 清 Redis（无人观看主路径）</li>
 *   <li><b>RTSP 源异常断开</b>（设备掉电 / ZLM 重启 / 网络中断）→ {@code on_stream_changed(regist=false)}
 *       同样调 {@link #cleanupByStreamId} 清 Redis（非正常路径）</li>
 * </ol>
 * <p>24h TTL 是兜底——以上三道全部漏调时 Redis 自己回收，避免永久僵尸。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnvifPlayServiceImpl implements OnvifPlayService {

    /** Redis Key 前缀（与接口常量 STREAM_ID_PREFIX 拼出完整 key） */
    private static final String REDIS_PREFIX = "onvif:play:";
    /** 拉流 rtp_type: 1=TCP */
    private static final int RTP_TYPE = 1;
    /**
     * Redis 记账硬上限 TTL。stop / hook 双双漏调时 Redis 自己回收，避免永久僵尸。
     * 选 24h 是因为 ONVIF 点播正常单次观看时长远小于此，仅作兜底。
     */
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    @Override
    public boolean isOnvifStream(String app, String stream) {
        return APP.equalsIgnoreCase(app) && stream != null && stream.startsWith(STREAM_ID_PREFIX);
    }

    private final OnvifDeviceMapper deviceMapper;
    private final MediaServerSelector mediaServerSelector;
    private final GbMediaServerMapper mediaServerMapper;
    private final ZlmRestClient zlmRestClient;
    private final MediaUrlBuilder urlBuilder;
    private final StringRedisTemplate redisTemplate;

    @Override
    public OnvifPlayResultResponse start(OnvifPlayStartRequest req) {
        if (req == null || req.getDeviceId() == null) {
            throw new ServiceException("deviceId 不能为空");
        }
        OnvifDevice device = deviceMapper.selectById(req.getDeviceId());
        if (device == null) {
            throw new ServiceException("设备不存在: " + req.getDeviceId());
        }
        if (device.getStreamUri() == null || device.getStreamUri().isBlank()) {
            throw new ServiceException("设备尚未探测出 RTSP 地址，请先点『探测』");
        }

        String streamId = STREAM_ID_PREFIX + device.getId();
        String redisKey = REDIS_PREFIX + streamId;

        // 幂等：已有活跃会话直接复用
        Map<Object, Object> exist = redisTemplate.opsForHash().entries(redisKey);
        if (!exist.isEmpty()) {
            String serverId = (String) exist.get("serverId");
            MediaServer server = findServer(serverId);
            if (server != null && zlmRestClient.isMediaOnline(server, APP, streamId)) {
                log.info("[ONVIF点播] 复用活跃会话 流ID: {}", streamId);
                return buildResult(server, streamId);
            }
            // Redis 有记录但流已断，先清掉再重新拉
            log.info("[ONVIF点播] 残留会话已失效，清扫后重拉 流ID: {}", streamId);
            String proxyKey = (String) exist.get("proxyKey");
            if (server != null) {
                zlmRestClient.delStreamProxy(server, proxyKey);
            }
            redisTemplate.delete(redisKey);
        }

        MediaServer server = mediaServerSelector.pick();
        String authorizedUrl = buildAuthorizedRtspUrl(device);
        String proxyKey;
        try {
            proxyKey = zlmRestClient.addStreamProxy(server, APP, streamId, authorizedUrl, RTP_TYPE);
        } catch (ServiceException e) {
            log.error("[ONVIF点播] addStreamProxy 失败 设备ID: {} 错误: {}", device.getId(), e.getMessage());
            throw e;
        }

        redisTemplate.opsForHash().putAll(redisKey, Map.of(
                "serverId", server.getServerId(),
                "proxyKey", proxyKey,
                "deviceId", String.valueOf(device.getId()),
                "startTime", String.valueOf(System.currentTimeMillis())
        ));
        // 兜底 TTL：stop / hook 三道清理路径全部漏调时 Redis 自己回收，避免永久僵尸。
        // putAll 不会重置 TTL，所以这里必须显式 set（之前可能残留过 TTL 也无副作用）。
        redisTemplate.expire(redisKey, SESSION_TTL);
        log.info("[ONVIF点播] 启动成功 设备ID: {} 流ID: {} 服务器ID: {}",
                device.getId(), streamId, server.getServerId());
        return buildResult(server, streamId);
    }

    @Override
    public void stop(OnvifPlayStopRequest req) {
        if (req == null || req.getStreamId() == null || req.getStreamId().isBlank()) {
            throw new ServiceException("streamId 不能为空");
        }
        String redisKey = REDIS_PREFIX + req.getStreamId();
        Map<Object, Object> exist = redisTemplate.opsForHash().entries(redisKey);
        if (exist.isEmpty()) {
            log.info("[ONVIF点播] 停止: {} 无对应会话（已清扫）", req.getStreamId());
            return;
        }
        String serverId = (String) exist.get("serverId");
        String proxyKey = (String) exist.get("proxyKey");
        MediaServer server = findServer(serverId);
        if (server != null) {
            zlmRestClient.delStreamProxy(server, proxyKey);
        } else {
            log.warn("[ONVIF点播] 停止时节点丢失 服务器ID: {}", serverId);
        }
        redisTemplate.delete(redisKey);
        log.info("[ONVIF点播] 停止成功 流ID: {}", req.getStreamId());
    }

    @Override
    public void cleanupByStreamId(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return;
        }
        String redisKey = REDIS_PREFIX + streamId;
        Boolean removed = redisTemplate.delete(redisKey);
        if (Boolean.TRUE.equals(removed)) {
            log.info("[ONVIF点播] 钩子清扫 流ID: {}", streamId);
        }
        // 不调 delStreamProxy：走到这一路时 ZLM 要么已主动断开（on_stream_none_reader
        // 回的 close=true），要么本来就是异常断开（on_stream_changed regist=false），
        // 代理早就没了。重复调用反而会 404。
    }

    @Override
    public List<OnvifSession> listAllActive() {
        Set<String> keys = redisTemplate.keys(REDIS_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<OnvifSession> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            try {
                Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
                if (hash == null || hash.isEmpty()) {
                    continue;
                }
                OnvifSession s = new OnvifSession();
                // key 形如 "onvif:play:onvif_42" → 截最后一段作 streamId
                int idx = key.lastIndexOf(':');
                s.setStreamId(idx >= 0 ? key.substring(idx + 1) : key);
                s.setServerId((String) hash.get("serverId"));
                Object deviceIdRaw = hash.get("deviceId");
                if (deviceIdRaw != null) {
                    try {
                        s.setDeviceId(Long.parseLong(deviceIdRaw.toString()));
                    } catch (NumberFormatException ignore) {
                        // 历史脏数据：deviceId 字段不是数字时跳过
                    }
                }
                s.setProxyKey((String) hash.get("proxyKey"));
                Object startMs = hash.get("startTime");
                if (startMs != null) {
                    try {
                        s.setStartTime(java.time.Instant.ofEpochMilli(Long.parseLong(startMs.toString()))
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                    } catch (NumberFormatException ignore) {
                        // 历史脏数据忽略
                    }
                }
                out.add(s);
            } catch (Exception e) {
                // 单条失败不阻塞整体——脏数据 / 网络抖动
                log.warn("[ONVIF点播] 会话解析失败 key={} err={}", key, e.getMessage());
            }
        }
        return out;
    }

    // ============================================================
    //  helpers
    // ============================================================

    private OnvifPlayResultResponse buildResult(MediaServer server, String streamId) {
        OnvifPlayResultResponse vo = new OnvifPlayResultResponse();
        vo.setStreamId(streamId);
        vo.setApp(APP);
        vo.setMediaServerId(server.getServerId());
        vo.setFlvUrl(urlBuilder.buildFlv(server, APP, streamId));
        vo.setWsFlvUrl(urlBuilder.buildWsFlv(server, APP, streamId));
        vo.setHlsUrl(urlBuilder.buildHls(server, APP, streamId));
        vo.setRtcUrl(urlBuilder.buildRtc(server, APP, streamId));
        return vo;
    }

    private MediaServer findServer(String serverId) {
        if (serverId == null) return null;
        return mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, serverId));
    }

    /**
     * 把用户名/密码塞进 RTSP URL：{@code rtsp://user:pwd@host:port/path}。
     * 账密里可能含特殊字符（@、:、/），必须 URLEncode。
     */
    private static String buildAuthorizedRtspUrl(OnvifDevice device) {
        String uri = device.getStreamUri();
        if (uri == null) {
            throw new ServiceException("stream_uri 为空");
        }
        String user = device.getUsername();
        if (user == null || user.isBlank()) {
            return uri;
        }
        int schemeEnd = uri.indexOf("://");
        if (schemeEnd < 0) {
            throw new ServiceException("非法的 RTSP URI: " + uri);
        }
        String scheme = uri.substring(0, schemeEnd + 3);
        String rest = uri.substring(schemeEnd + 3);
        // 有些设备返回的 uri 里可能已经带了账密，先剥掉
        int atIdx = rest.indexOf('@');
        int slashIdx = rest.indexOf('/');
        if (atIdx > 0 && (slashIdx < 0 || atIdx < slashIdx)) {
            rest = rest.substring(atIdx + 1);
        }
        String encUser = URLEncoder.encode(user, StandardCharsets.UTF_8);
        String encPwd = device.getPassword() == null ? ""
                : URLEncoder.encode(device.getPassword(), StandardCharsets.UTF_8);
        return scheme + encUser + ":" + encPwd + "@" + rest;
    }
}
