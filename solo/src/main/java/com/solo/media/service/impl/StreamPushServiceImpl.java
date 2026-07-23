package com.solo.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.solo.common.ServiceException;
import com.solo.media.MediaUrlBuilder;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.entity.StreamPush;
import com.solo.media.mapper.StreamPushMapper;
import com.solo.media.request.StreamPushPageRequest;
import com.solo.media.response.StreamPushPlayUrlResponse;
import com.solo.media.service.StreamPushService;
import com.solo.media.service.StreamPushClientService;
import com.solo.media.service.MediaServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 推流服务实现
 *
 * <p>入表来源只有 ZLM Hook 的注册事件，判定推流类型用 ZLM 上报的 {@code originTypeStr}
 * 字符串（{@code rtmp_push / rtsp_push / rtc_push}），而不是整数 ordinal——
 * ZLM 未来在枚举中间插入新类型时 ordinal 会错位。国标 RTP 推送 / 拉流代理 / VOD 都会被过滤掉。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamPushServiceImpl extends ServiceImpl<StreamPushMapper, StreamPush>
        implements StreamPushService {

    private final ZlmRestClient zlmRestClient;
    private final MediaServerService mediaServerService;
    private final MediaUrlBuilder urlBuilder;
    private final StreamPushClientService streamPushClientService;

    @Override
    public Page<StreamPush> pageQuery(StreamPushPageRequest req) {
        LambdaQueryWrapper<StreamPush> w = new LambdaQueryWrapper<>();
        if (req.getApp() != null && !req.getApp().isBlank()) {
            w.eq(StreamPush::getApp, req.getApp());
        }
        if (req.getStream() != null && !req.getStream().isBlank()) {
            w.like(StreamPush::getStream, req.getStream());
        }
        if (req.getMediaServerId() != null && !req.getMediaServerId().isBlank()) {
            w.eq(StreamPush::getMediaServerId, req.getMediaServerId());
        }
        if (req.getPushing() != null) {
            w.eq(StreamPush::getPushing, req.getPushing());
        }
        w.orderByDesc(StreamPush::getCreateTime);
        return page(new Page<>(req.getPage(), req.getPageSize()), w);
    }

    @Override
    public StreamPushPlayUrlResponse getPlayUrls(Long id) {
        StreamPush p = getById(id);
        if (p == null) {
            throw new ServiceException("推流不存在: " + id);
        }
        StreamPushPlayUrlResponse vo = new StreamPushPlayUrlResponse();
        vo.setApp(p.getApp());
        vo.setStream(p.getStream());
        if (p.getPushing() == null || p.getPushing() != 1 || p.getMediaServerId() == null) {
            return vo;
        }
        MediaServer server = findServer(p.getMediaServerId());
        if (server == null) return vo;
        vo.setMediaServerId(server.getServerId());
        vo.setFlvUrl(urlBuilder.buildFlv(server, p.getApp(), p.getStream()));
        vo.setWsFlvUrl(urlBuilder.buildWsFlv(server, p.getApp(), p.getStream()));
        vo.setHlsUrl(urlBuilder.buildHls(server, p.getApp(), p.getStream()));
        vo.setRtcUrl(urlBuilder.buildRtc(server, p.getApp(), p.getStream()));
        return vo;
    }

    @Override
    public void forceStop(Long id) {
        StreamPush p = getById(id);
        if (p == null) {
            throw new ServiceException("推流不存在: " + id);
        }
        MediaServer server = findServer(p.getMediaServerId());
        if (server == null) {
            // 节点没了也把本地行标离线
            p.setPushing(0);
            updateById(p);
            log.warn("[推流] forceStop 时节点已丢失 serverId={}", p.getMediaServerId());
            return;
        }
        zlmRestClient.closeStream(server, p.getApp(), p.getStream(), true);
        // 是否真删由后续 on_stream_changed(regist=false) 兜底，这里先标离线避免用户以为没关
        p.setPushing(0);
        updateById(p);
        log.info("[推流] forceStop id={} app={} stream={}", id, p.getApp(), p.getStream());
    }

    @Override
    public void deleteById(Long id) {
        StreamPush p = getById(id);
        if (p == null) return;
        if (p.getPushing() != null && p.getPushing() == 1) {
            throw new ServiceException("推流仍在推送，请先『强制中止』再删除");
        }
        removeById(id);
        log.info("[推流] 删除记录 id={} app={} stream={}", id, p.getApp(), p.getStream());
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            try {
                deleteById(id);
            } catch (Exception e) {
                log.warn("[推流] 批量删除跳过 id={} err={}", id, e.getMessage());
            }
        }
    }

    @Override
    public void onStreamRegist(String app, String stream, String mediaServerId, Map<String, Object> body) {
        if (app == null || stream == null || mediaServerId == null) return;
        String originType = mapOriginType(stringOf(body, "originTypeStr"));
        if (originType == null) return;

        StreamPush old = lambdaQuery()
                .eq(StreamPush::getApp, app)
                .eq(StreamPush::getStream, stream).one();
        StreamPush entity = old == null ? new StreamPush() : old;
        entity.setApp(app);
        entity.setStream(stream);
        entity.setMediaServerId(mediaServerId);
        entity.setVhost(stringOf(body, "vhost"));
        entity.setStreamSchema(stringOf(body, "schema"));
        entity.setOriginType(originType);
        entity.setPushing(1);
        entity.setAliveSecond(intOf(body, "aliveSecond"));
        // 反查 client_id 做溯源；未开启鉴权 or 找不到时为空
        String token = parseParam(stringOf(body, "params"), "token");
        entity.setClientId(streamPushClientService.clientIdOfToken(token));
        if (old == null) {
            entity.setPushTime(LocalDateTime.now());
            save(entity);
            log.info("[推流] 新增 app={} stream={} server={} originType={}",
                    app, stream, mediaServerId, originType);
        } else {
            updateById(entity);
            log.info("[推流] 更新 id={} app={} stream={}", entity.getId(), app, stream);
        }
    }

    @Override
    public void onStreamCancel(String app, String stream, String mediaServerId) {
        if (app == null || stream == null) return;
        StreamPush old = lambdaQuery()
                .eq(StreamPush::getApp, app)
                .eq(StreamPush::getStream, stream).one();
        if (old == null) return;
        // 直接物理删（solo 未启用级联，不需要保留"离线记录"）
        removeById(old.getId());
        log.info("[推流] 注销 id={} app={} stream={}", old.getId(), app, stream);
    }

    @Override
    public void reconcileWithZlm(String mediaServerId) {
        MediaServer server = findServer(mediaServerId);
        if (server == null) {
            log.warn("[推流] reconcile 时节点不存在 serverId={}", mediaServerId);
            return;
        }
        List<JsonNode> mediaList = zlmRestClient.getMediaList(server);
        // ZLM 现存 push 流的 app/stream 集合
        Set<String> live = new HashSet<>();
        for (JsonNode item : mediaList) {
            if (mapOriginType(item.path("originTypeStr").asText(null)) == null) continue;
            String schema = item.path("schema").asText(null);
            // ZLM 每条流按 schema 会重复出现（rtsp/rtmp/hls/ts/fmp4），我们只按 rtsp 计一次
            if (!"rtsp".equalsIgnoreCase(schema)) continue;
            String app = item.path("app").asText(null);
            String stream = item.path("stream").asText(null);
            if (app == null || stream == null) continue;
            live.add(app + "|" + stream);
        }

        // 库里 push 但 ZLM 没有 → 删掉（僵尸行）
        List<StreamPush> dbAll = lambdaQuery()
                .eq(StreamPush::getMediaServerId, mediaServerId).list();
        int cleaned = 0;
        for (StreamPush row : dbAll) {
            if (!live.contains(row.getApp() + "|" + row.getStream())) {
                removeById(row.getId());
                cleaned++;
            }
        }
        log.info("[推流] reconcile 完成 server={} zlmPush={} dbBefore={} 清理僵尸={} ",
                mediaServerId, live.size(), dbAll.size(), cleaned);
    }

    // ---------------- helpers ----------------

    private MediaServer findServer(String serverId) {
        if (serverId == null) return null;
        return mediaServerService.lambdaQuery()
                .eq(MediaServer::getServerId, serverId).one();
    }

    /**
     * ZLM originTypeStr → 业务字符串。
     * <p>只放行 {@code rtmp_push / rtsp_push / rtc_push} 这三种"客户端主动推上来"的场景；
     * 其余（rtp_push=国标、pull=拉流代理、mp4_vod、device_chn 等）返回 null。</p>
     */
    private static String mapOriginType(String originTypeStr) {
        if (originTypeStr == null) return null;
        return switch (originTypeStr) {
            case "rtmp_push" -> "rtmp";
            case "rtsp_push" -> "rtsp";
            case "rtc_push"  -> "rtc";
            default -> null;
        };
    }

    private static String stringOf(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }

    private static int intOf(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 解析 ZLM 上报的 params 串（形如 {@code token=abc&other=1}）取指定 key */
    private static String parseParam(String params, String key) {
        if (params == null || params.isEmpty() || key == null) return null;
        String decoded = decodeHtmlEntities(params);
        for (String pair : decoded.split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            if (key.equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }

    /**
     * ZLM 上报 params 时会把 {@code =} / {@code &} 做 HTML 实体转义（{@code &#61;} / {@code &#38;}），
     * 拆分前先跑一遍简单解码。
     */
    private static String decodeHtmlEntities(String s) {
        if (s == null || s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '&') { sb.append(c); i++; continue; }
            int semi = s.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 8) { sb.append(c); i++; continue; }
            String ent = s.substring(i + 1, semi);
            char decoded;
            try {
                if (ent.startsWith("#x") || ent.startsWith("#X")) {
                    decoded = (char) Integer.parseInt(ent.substring(2), 16);
                } else if (ent.startsWith("#")) {
                    decoded = (char) Integer.parseInt(ent.substring(1));
                } else {
                    decoded = switch (ent) {
                        case "amp" -> '&';
                        case "lt" -> '<';
                        case "gt" -> '>';
                        case "quot" -> '"';
                        case "apos" -> '\'';
                        default -> 0;
                    };
                    if (decoded == 0) { sb.append(c); i++; continue; }
                }
            } catch (NumberFormatException e) {
                sb.append(c);
                i++;
                continue;
            }
            sb.append(decoded);
            i = semi + 1;
        }
        return sb.toString();
    }
}
