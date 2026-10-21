package com.solo.media;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.properties.MediaProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerBootstrap {

    private final MediaProperties mediaProperties;
    private final GbMediaServerMapper mediaServerMapper;

    @PostConstruct
    void init() {
        // 启动期显式检查 hook secret 配置
        String hookSecret = mediaProperties.getHookSecret();
        if (hookSecret == null || hookSecret.isBlank()) {
            log.error("[流媒体] media.hook-secret 未配置，ZLM 所有 hook 回调都会被拒。请在 application.yaml 补齐。");
        } else {
            log.info("[流媒体] hook secret 已启用，长度={}", hookSecret.length());
        }

        MediaProperties.DefaultServer cfg = mediaProperties.getDefaultServer();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
        if (cfg.getServerId() == null || cfg.getServerId().isBlank()) {
            log.warn("[流媒体] default-server.server-id 为空，跳过默认节点自举");
            return;
        }
        MediaServer exist = mediaServerMapper.selectOne(
                new LambdaQueryWrapper<MediaServer>().eq(MediaServer::getServerId, cfg.getServerId()));
        if (exist != null) {
            // 已存在则以配置为准整体更新，保留 id / status / hookAliveTime 等运行期状态
            applyConfig(exist, cfg);
            mediaServerMapper.updateById(exist);
            log.info("[流媒体] 默认 ZLM 节点已更新 serverId={} {}:{}",
                    cfg.getServerId(), cfg.getHostIp(), cfg.getHttpPort());
            return;
        }
        MediaServer server = new MediaServer();
        applyConfig(server, cfg);
        server.setStatus(0);
        mediaServerMapper.insert(server);
        log.info("[流媒体] 默认 ZLM 节点已写入 serverId={} {}:{}",
                cfg.getServerId(), cfg.getHostIp(), cfg.getHttpPort());
    }

    /**
     * 将配置中的默认节点字段应用到实体上（不含 id / status / hookAliveTime 等运行期状态）。
     */
    private void applyConfig(MediaServer server, MediaProperties.DefaultServer cfg) {
        server.setServerId(cfg.getServerId());
        server.setName(cfg.getName());
        server.setHostIp(cfg.getHostIp());
        server.setHttpPort(cfg.getHttpPort());
        server.setSecret(cfg.getSecret());
        server.setStreamIp(cfg.getStreamIp() == null || cfg.getStreamIp().isBlank()
                ? cfg.getHostIp() : cfg.getStreamIp());
        server.setStreamHttpPort(cfg.getStreamHttpPort());
        server.setRtcPort(cfg.getRtcPort());
    }
}
