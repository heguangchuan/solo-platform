package com.solo.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

import java.util.List;

/**
 * 拉流代理服务实现
 *
 * <p>ZLM 侧的接口只有 addStreamProxy / delStreamProxy 两条，所以：</p>
 * <ul>
 *   <li>enable → 选节点 → addStreamProxy → 回填 proxy_key/status/mediaServerId</li>
 *   <li>disable → delStreamProxy → 清 proxy_key/status</li>
 *   <li>saveOrUpdate 时若原来在推流：先 disable 再按新 enabled 决定要不要重推</li>
 *   <li>delete 先 disable 再删记录</li>
 * </ul>
 * <p>失败原因写 last_error 便于前端展示，不抛给用户面（新增/编辑保存本身不因 ZLM 不通失败）。</p>
 */
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
        if (req.getName() != null && !req.getName().isBlank()) {
            w.like(StreamProxy::getName, req.getName());
        }
        if (req.getApp() != null && !req.getApp().isBlank()) {
            w.eq(StreamProxy::getApp, req.getApp());
        }
        if (req.getStream() != null && !req.getStream().isBlank()) {
            w.like(StreamProxy::getStream, req.getStream());
        }
        if (req.getStatus() != null) {
            w.eq(StreamProxy::getStatus, req.getStatus());
        }
        if (req.getEnabled() != null) {
            w.eq(StreamProxy::getEnabled, req.getEnabled());
        }
        w.orderByDesc(StreamProxy::getUpdateTime);
        return page(new Page<>(req.getPage(), req.getPageSize()), w);
    }

    @Override
    public void saveOrUpdateProxy(StreamProxy proxy) {
        validate(proxy);
        applyDefaults(proxy);

        StreamProxy old = proxy.getId() == null ? null : getById(proxy.getId());
        // 编辑且原来已推流：先摘掉旧的
        if (old != null && old.getStatus() != null && old.getStatus() == 1) {
            tryDisableOnZlm(old);
            proxy.setProxyKey(null);
            proxy.setStatus(0);
        } else if (old != null) {
            // 保留原始运行态（未启用时全是 0）
            proxy.setProxyKey(old.getProxyKey());
            proxy.setStatus(old.getStatus());
        } else {
            proxy.setProxyKey(null);
            proxy.setStatus(0);
        }
        proxy.setLastError(null);
        saveOrUpdate(proxy);
        log.info("[拉流代理] 保存 id={} app={} stream={} enabled={}",
                proxy.getId(), proxy.getApp(), proxy.getStream(), proxy.getEnabled());

        if (proxy.getEnabled() != null && proxy.getEnabled() == 1) {
            enable(proxy.getId());
        }
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
        try {
            String key = zlmRestClient.addStreamProxy(
                    server,
                    p.getApp(),
                    p.getStream(),
                    p.getSrcUrl(),
                    p.getRtpType() == null ? 1 : p.getRtpType(),
                    p.getEnableAudio() != null && p.getEnableAudio() == 1,
                    p.getEnableMp4() != null && p.getEnableMp4() == 1);
            p.setProxyKey(key);
            p.setStatus(1);
            p.setEnabled(1);
            p.setMediaServerId(server.getServerId());
            p.setLastError(null);
            updateById(p);
            log.info("[拉流代理] enable ok id={} server={} key={}", id, server.getServerId(), key);
        } catch (Exception e) {
            p.setStatus(0);
            p.setProxyKey(null);
            p.setLastError(shorten(e.getMessage()));
            updateById(p);
            log.warn("[拉流代理] enable 失败 id={} err={}", id, e.getMessage());
            throw new ServiceException("推流到 ZLM 失败：" + e.getMessage());
        }
    }

    @Override
    public void disable(Long id) {
        StreamProxy p = getById(id);
        if (p == null) {
            throw new ServiceException("代理不存在: " + id);
        }
        tryDisableOnZlm(p);
        p.setEnabled(0);
        p.setStatus(0);
        p.setProxyKey(null);
        p.setLastError(null);
        updateById(p);
        log.info("[拉流代理] disable ok id={}", id);
    }

    @Override
    public void deleteById(Long id) {
        StreamProxy p = getById(id);
        if (p == null) return;
        tryDisableOnZlm(p);
        removeById(id);
        log.info("[拉流代理] 删除 id={} app={} stream={}", id, p.getApp(), p.getStream());
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
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
        if (p.getApp() == null || p.getApp().isBlank()) p.setApp("proxy");
        if (p.getRtpType() == null) p.setRtpType(1);
        if (p.getEnableAudio() == null) p.setEnableAudio(1);
        if (p.getEnableMp4() == null) p.setEnableMp4(0);
        if (p.getEnableDisableNoneReader() == null) p.setEnableDisableNoneReader(0);
        if (p.getEnabled() == null) p.setEnabled(1);
    }

    private MediaServer pickServer(StreamProxy p) {
        if (p.getMediaServerId() != null && !p.getMediaServerId().isBlank()) {
            MediaServer s = mediaServerMapper.selectOne(
                    new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, p.getMediaServerId()));
            if (s != null) return s;
            log.warn("[拉流代理] 指定节点不存在，回退自动选择 serverId={}", p.getMediaServerId());
        }
        return mediaServerSelector.pick();
    }

    private void tryDisableOnZlm(StreamProxy p) {
        if (p.getProxyKey() == null || p.getMediaServerId() == null) return;
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
        if (s == null) return null;
        return s.length() > 490 ? s.substring(0, 490) : s;
    }
}
