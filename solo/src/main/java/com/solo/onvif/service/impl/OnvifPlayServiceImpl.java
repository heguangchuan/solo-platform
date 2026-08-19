package com.solo.onvif.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.media.MediaUrlBuilder;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.service.MediaServerSelector;
import com.solo.onvif.entity.OnvifDevice;
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
import java.util.Map;

/**
 * ONVIF 实时点播服务实现
 *
 * <h3>流程（start）</h3>
 * <ol>
 *   <li>校验设备已探测出 stream_uri</li>
 *   <li>幂等：Redis 中已存在同 streamId → 直接返回原播放 URL</li>
 *   <li>拼装带账密的 RTSP URL（URLEncoder 转账密特殊字符）</li>
 *   <li>{@link ZlmRestClient#addStreamProxy} 让 ZLM 拉流，rtp_type=1（TCP）</li>
 *   <li>拼装 flv/wsFlv/hls/rtc 播放地址返回</li>
 * </ol>
 *
 * <p>与国标 {@code GbPlayServiceImpl} 相比省掉了 SIP INVITE、SSRC 分配、on_stream_changed 等待、
 * 超时清扫等一大堆流程，因为 ZLM 自己负责与源建立 RTSP 连接。</p>
 *
 * <p><b>会话记账走 Redis</b>：Key {@code onvif:play:<streamId>} 存 hash
 * {@code {serverId, proxyKey, deviceId, startTime}}，无 TTL（stop 时删）。
 * 跟国标 GbInviteSession 迁 Redis 的路径一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnvifPlayServiceImpl implements OnvifPlayService {

    /** ZLM app 名（与国标 rtp 区分） */
    private static final String APP = "rtsp";
    /** Redis Key 前缀 */
    private static final String REDIS_PREFIX = "onvif:play:";
    /** 拉流 rtp_type: 1=TCP */
    private static final int RTP_TYPE = 1;

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

        String streamId = "onvif_" + device.getId();
        String redisKey = REDIS_PREFIX + streamId;

        // 幂等：已有活跃会话直接复用
        Map<Object, Object> exist = redisTemplate.opsForHash().entries(redisKey);
        if (!exist.isEmpty()) {
            String serverId = (String) exist.get("serverId");
            MediaServer server = findServer(serverId);
            if (server != null && zlmRestClient.isMediaOnline(server, APP, streamId)) {
                log.info("[ONVIF点播] 复用活跃会话 stream={}", streamId);
                return buildResult(server, streamId);
            }
            // Redis 有记录但流已断，先清掉再重新拉
            log.info("[ONVIF点播] 残留会话已失效，清扫后重拉 stream={}", streamId);
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
            proxyKey = zlmRestClient.addStreamProxy(server, APP, streamId, authorizedUrl,
                    RTP_TYPE, true, false);
        } catch (ServiceException e) {
            log.error("[ONVIF点播] addStreamProxy 失败 device={} err={}", device.getId(), e.getMessage());
            throw e;
        }

        redisTemplate.opsForHash().putAll(redisKey, Map.of(
                "serverId", server.getServerId(),
                "proxyKey", proxyKey,
                "deviceId", String.valueOf(device.getId()),
                "startTime", String.valueOf(System.currentTimeMillis())
        ));
        log.info("[ONVIF点播] start ok device={} stream={} server={}",
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
            log.info("[ONVIF点播] stop: {} 无对应会话（已清扫）", req.getStreamId());
            return;
        }
        String serverId = (String) exist.get("serverId");
        String proxyKey = (String) exist.get("proxyKey");
        MediaServer server = findServer(serverId);
        if (server != null) {
            zlmRestClient.delStreamProxy(server, proxyKey);
        } else {
            log.warn("[ONVIF点播] stop 时节点丢失 serverId={}", serverId);
        }
        redisTemplate.delete(redisKey);
        log.info("[ONVIF点播] stop ok stream={}", req.getStreamId());
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
