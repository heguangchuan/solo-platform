package com.solo.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import tools.jackson.databind.JsonNode;
import com.solo.common.ServiceException;
import com.solo.media.MediaUrlBuilder;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.entity.StreamProxy;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.mapper.StreamProxyMapper;
import com.solo.media.request.StreamProxyPageRequest;
import com.solo.media.response.StreamProxyPlayUrlResponse;
import com.solo.media.service.StreamProxyService;
import com.solo.media.service.MediaServerSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
public class StreamProxyServiceImpl extends ServiceImpl<StreamProxyMapper, StreamProxy>
        implements StreamProxyService {

    private final ZlmRestClient zlmRestClient;
    private final MediaServerSelector mediaServerSelector;
    private final GbMediaServerMapper mediaServerMapper;
    private final MediaUrlBuilder urlBuilder;

    @Override
    public Page<StreamProxy> pageQuery(StreamProxyPageRequest req) {
        LambdaQueryWrapper<StreamProxy> w = new LambdaQueryWrapper<>();
        if (req.getApp() != null && !req.getApp().isBlank()) {
            w.eq(StreamProxy::getApp, req.getApp());
        }
        if (req.getStream() != null && !req.getStream().isBlank()) {
            w.like(StreamProxy::getStream, req.getStream());
        }
        if (req.getStatus() != null) {
            w.eq(StreamProxy::getStatus, req.getStatus());
        }
        w.orderByDesc(StreamProxy::getUpdateTime);
        return page(new Page<>(req.getPage(), req.getPageSize()), w);
    }

    @Override
    public void saveOrUpdateProxy(StreamProxy proxy) {
        validate(proxy);
        applyDefaults(proxy);

        StreamProxy old = proxy.getId() == null ? null : getById(proxy.getId());
        // 编辑且原来在推：先摘掉旧的，保证后续用最新 srcUrl/rtpType 重启
        if (old != null && old.getStatus() != null && old.getStatus() == 1) {
            tryDisableOnZlm(old);
        }
        // 清运行时字段 + lastError，等 enable() 重写
        proxy.setProxyKey(null);
        proxy.setStatus(0);
        proxy.setLastError(null);
        saveOrUpdate(proxy);
        log.info("[拉流代理] 保存 id={} app={} stream={}",
                proxy.getId(), proxy.getApp(), proxy.getStream());

        // 保存即应用：自动尝试启动。enable() 失败时已在内部记 lastError 并抛 ServiceException，
        // 配置仍然入库（status=0 + lastError），前端能正常刷出"未运行 + 错误原因"
        enable(proxy.getId());
    }

    @Override
    public void enable(Long id) {
        StreamProxy p = getById(id);
        if (p == null) {
            throw new ServiceException("代理不存在: " + id);
        }
        if (p.getStatus() != null && p.getStatus() == 1 && p.getProxyKey() != null) {
            log.info("[拉流代理] enable 忽略：已在推流 id={} stream={}", id, p.getStream());
            return;
        }
        MediaServer server = pickServer(p);
        // 对账器标离线时保留了 proxyKey；这里先尝试 del 旧条目清理 ZLM 残留
        // （ZLM pull worker 重试期间 getMediaList 看不到，但 proxy 列表还在，会导致 addStreamProxy 报 "already exists"）
        // del 失败无所谓（ZLM 已彻底回收旧 key 也可能），接着 add 即可
        if (p.getProxyKey() != null) {
            try {
                zlmRestClient.delStreamProxy(server, p.getProxyKey());
                log.info("[拉流代理] enable 前清理残留 proxyKey={}", p.getProxyKey());
            } catch (Exception e) {
                log.debug("[拉流代理] del 旧 key 失败（无所谓）err={}", e.getMessage());
            }
        }
        try {
            String key = zlmRestClient.addStreamProxy(
                    server,
                    p.getApp(),
                    p.getStream(),
                    p.getSrcUrl(),
                    p.getRtpType() == null ? 0 : p.getRtpType());
            p.setProxyKey(key);
            p.setStatus(1);
            p.setMediaServerId(server.getServerId());
            p.setLastError(null);
            updateById(p);
            log.info("[拉流代理] enable ok id={} server={} key={}", id, server.getServerId(), key);
        } catch (Exception e) {
            String friendly = friendlyError(e.getMessage());
            p.setStatus(0);
            p.setLastError(shorten(friendly));
            updateById(p);
            // 日志保留原始错误便于排查
            log.warn("[拉流代理] enable 失败 id={} raw={}", id, e.getMessage());
            throw new ServiceException(friendly);
        }
    }

    @Override
    public void disable(Long id) {
        StreamProxy p = getById(id);
        if (p == null) {
            throw new ServiceException("代理不存在: " + id);
        }
        tryDisableOnZlm(p);
        p.setStatus(0);
        p.setProxyKey(null);
        p.setLastError(null);
        updateById(p);
        log.info("[拉流代理] disable ok id={}", id);
    }

    @Override
    public void deleteById(Long id) {
        StreamProxy p = getById(id);
        if (p == null) {
            return;
        }
        tryDisableOnZlm(p);
        removeById(id);
        log.info("[拉流代理] 删除 id={} app={} stream={}", id, p.getApp(), p.getStream());
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            try {
                deleteById(id);
            } catch (Exception e) {
                log.warn("[拉流代理] 批量删除跳过 id={} err={}", id, e.getMessage());
            }
        }
    }

    @Override
    public StreamProxyPlayUrlResponse getPlayUrls(Long id) {
        StreamProxy p = getById(id);
        if (p == null) {
            throw new ServiceException("代理不存在: " + id);
        }
        StreamProxyPlayUrlResponse vo = new StreamProxyPlayUrlResponse();
        vo.setApp(p.getApp());
        vo.setStream(p.getStream());
        if (p.getStatus() == null || p.getStatus() != 1 || p.getMediaServerId() == null) {
            return vo;
        }
        MediaServer server = mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, p.getMediaServerId()));
        if (server == null) {
            return vo;
        }
        vo.setMediaServerId(server.getServerId());
        vo.setFlvUrl(urlBuilder.buildFlv(server, p.getApp(), p.getStream()));
        vo.setWsFlvUrl(urlBuilder.buildWsFlv(server, p.getApp(), p.getStream()));
        vo.setHlsUrl(urlBuilder.buildHls(server, p.getApp(), p.getStream()));
        vo.setRtcUrl(urlBuilder.buildRtc(server, p.getApp(), p.getStream()));
        return vo;
    }

    // ---------------- helpers ----------------

    private void validate(StreamProxy p) {
        if (p.getStream() == null || p.getStream().isBlank()) {
            throw new ServiceException("stream 不能为空");
        }
        if (p.getSrcUrl() == null || p.getSrcUrl().isBlank()) {
            throw new ServiceException("源地址不能为空");
        }
        // app+stream 唯一
        LambdaQueryWrapper<StreamProxy> w = new LambdaQueryWrapper<StreamProxy>()
                .eq(StreamProxy::getApp, p.getApp() == null || p.getApp().isBlank() ? "proxy" : p.getApp())
                .eq(StreamProxy::getStream, p.getStream());
        if (p.getId() != null) {
            w.ne(StreamProxy::getId, p.getId());
        }
        if (count(w) > 0) {
            throw new ServiceException("app + stream 已存在：" + (p.getApp() == null ? "proxy" : p.getApp()) + "/" + p.getStream());
        }
    }

    private void applyDefaults(StreamProxy p) {
        if (p.getApp() == null || p.getApp().isBlank()) {
            p.setApp("proxy");
        }
        if (p.getRtpType() == null) {
            p.setRtpType(0);
        }
    }

    private MediaServer pickServer(StreamProxy p) {
        if (p.getMediaServerId() != null && !p.getMediaServerId().isBlank()) {
            MediaServer s = mediaServerMapper.selectOne(
                    new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, p.getMediaServerId()));
            if (s != null) {
                return s;
            }
            log.warn("[拉流代理] 指定节点不存在，回退自动选择 serverId={}", p.getMediaServerId());
        }
        return mediaServerSelector.pick();
    }

    private void tryDisableOnZlm(StreamProxy p) {
        if (p.getProxyKey() == null || p.getMediaServerId() == null) {
            return;
        }
        MediaServer server = mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, p.getMediaServerId()));
        if (server == null) {
            log.warn("[拉流代理] 摘流时目标节点丢失 serverId={}", p.getMediaServerId());
            return;
        }
        try {
            zlmRestClient.delStreamProxy(server, p.getProxyKey());
        } catch (Exception e) {
            log.warn("[拉流代理] delStreamProxy 忽略 id={} err={}", p.getId(), e.getMessage());
        }
    }

    private static String shorten(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 490 ? s.substring(0, 490) : s;
    }

    /**
     * 把 ZLM 原始错误（英文 + URL + 方法名）翻译成用户能看懂的中文短句。
     * 列表 tooltip 和保存/启用的 toast 都消费这个返回值。
     */
    static String friendlyError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "推流失败";
        }
        String m = raw.toLowerCase();
        if (m.contains("timed out") || m.contains("timeout")) {
            return "源流连接超时，请检查源 URL 是否可达";
        }
        if (m.contains("connection refused") || m.contains("connect failed") || m.contains("connection failed")) {
            return "ZLM 节点不可达，请检查节点状态";
        }
        if (m.contains("401") || m.contains("unauthorized")) {
            return "源 RTSP 鉴权失败，请检查账号密码";
        }
        if (m.contains("404") || m.contains("not found")) {
            return "源流地址不存在，请检查 URL 路径";
        }
        if (m.contains("already exists")) {
            return "ZLM 中已存在该流，请稍后重试";
        }
        if (m.contains("unsupported") || m.contains("not support")) {
            return "ZLM 不支持该源协议，请检查 URL 协议类型";
        }
        // 兜底：去掉技术前缀，给一句"拉流失败"开头
        return "拉流失败：" + raw;
    }

    @Override
    public void reconcileWithZlm(String mediaServerId) {
        MediaServer server = mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, mediaServerId));
        if (server == null) {
            log.debug("[拉流代理对账] 节点未登记，跳过 serverId={}", mediaServerId);
            return;
        }
        List<JsonNode> mediaList = zlmRestClient.getMediaList(server);
        // ZLM 上现存的 (app|stream) 集合
        Set<String> live = new HashSet<>();
        for (JsonNode item : mediaList) {
            String app = item.path("app").asText(null);
            String stream = item.path("stream").asText(null);
            if (app == null || stream == null) continue;
            live.add(app + "|" + stream);
        }

        // 库里有 status=1 但 ZLM 已经没这条流的 → 标离线
        // 注意：保留 proxyKey 不清——后面 enable() 要用旧 key 先 del 残留（ZLM pull worker 重试期间
        // getMediaList 看不到但 proxy 列表还有，会导致 addStreamProxy 报 "already exists"）
        List<StreamProxy> running = lambdaQuery()
                .eq(StreamProxy::getMediaServerId, mediaServerId)
                .eq(StreamProxy::getStatus, 1)
                .list();
        int cleaned = 0;
        for (StreamProxy row : running) {
            if (!live.contains(row.getApp() + "|" + row.getStream())) {
                row.setStatus(0);
                row.setLastError("源流已不可达");
                updateById(row);
                cleaned++;
            }
        }
        // 每轮都打，方便确认任务在跑（包括"啥都没清"的情况）
        log.info("[拉流代理对账] server={} zlm流数={} 库在推={} 标离线={}",
                mediaServerId, live.size(), running.size(), cleaned);
    }
}
