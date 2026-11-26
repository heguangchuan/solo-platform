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

// ONVIF 不用走 SIP，ZLM 直接拉 RTSP。
// stop、无人观看、源流断开都会清 Redis；24h TTL 只负责收拾前面全漏掉的残留。
@Slf4j
@Service
@RequiredArgsConstructor
public class OnvifPlayServiceImpl implements OnvifPlayService {

    private static final String REDIS_PREFIX = "onvif:play:";
    private static final int RTP_TYPE = 1;
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

        // Redis 有记录不代表 ZLM 上流还在，复用前再确认一下
        Map<Object, Object> exist = redisTemplate.opsForHash().entries(redisKey);
        if (!exist.isEmpty()) {
            String serverId = (String) exist.get("serverId");
            MediaServer server = findServer(serverId);
            if (server != null && zlmRestClient.isMediaOnline(server, APP, streamId)) {
                log.info("[ONVIF点播] 复用活跃会话 流ID: {}", streamId);
                return buildResult(server, streamId);
            }
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
        // putAll 不会重置 TTL，这里得自己设
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
        // 到这里 ZLM 已经关流或源流已经断了，再调 delStreamProxy 只会收获 404
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
                int idx = key.lastIndexOf(':');
                s.setStreamId(idx >= 0 ? key.substring(idx + 1) : key);
                s.setServerId((String) hash.get("serverId"));
                Object deviceIdRaw = hash.get("deviceId");
                if (deviceIdRaw != null) {
                    try {
                        s.setDeviceId(Long.parseLong(deviceIdRaw.toString()));
                    } catch (NumberFormatException ignore) {
                        // 老数据里这个字段可能不是数字
                    }
                }
                s.setProxyKey((String) hash.get("proxyKey"));
                Object startMs = hash.get("startTime");
                if (startMs != null) {
                    try {
                        s.setStartTime(java.time.Instant.ofEpochMilli(Long.parseLong(startMs.toString()))
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                    } catch (NumberFormatException ignore) {
                        // 同上，脏数据跳过就行
                    }
                }
                out.add(s);
            } catch (Exception e) {
                // 一条脏了别拖累整页
                log.warn("[ONVIF点播] 会话解析失败 key={} err={}", key, e.getMessage());
            }
        }
        return out;
    }

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

    // 账密可能有 @、:、/，必须编码；设备 URI 里已有的旧账密先剥掉
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
