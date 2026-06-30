package com.solo.gb28181.media;

import com.solo.gb28181.config.MediaProperties;
import com.solo.gb28181.entity.GbMediaServer;
import com.solo.gb28181.service.GbMediaServerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时根据 application.yaml 中 {@code gb28181.media.default-server.*} 自动 upsert
 * 一个默认 ZLM 节点。
 * <p>这样部署只需启动 ZLM + solo 即可完成对接：solo 启动后默认节点已就绪，
 * ZLM hook 进来即可匹配。生产环境一般通过管理面再手工调整 hostIp / streamIp 等字段。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerBootstrap {

    private final MediaProperties mediaProperties;
    private final GbMediaServerService mediaServerService;

    @PostConstruct
    void init() {
        MediaProperties.DefaultServer cfg = mediaProperties.getDefaultServer();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
        if (cfg.getServerId() == null || cfg.getServerId().isBlank()) {
            log.warn("[流媒体] default-server.server-id 为空，跳过默认节点自举");
            return;
        }
        GbMediaServer exist = mediaServerService.getByServerId(cfg.getServerId());
        if (exist != null) {
            // 已存在则只在缺字段时回填，不覆盖运维手工改过的内容
            boolean dirty = false;
            if (exist.getHostIp() == null || exist.getHostIp().isBlank()) {
                exist.setHostIp(cfg.getHostIp());
                dirty = true;
            }
            if (exist.getHttpPort() == null) {
                exist.setHttpPort(cfg.getHttpPort());
                dirty = true;
            }
            if (exist.getSecret() == null || exist.getSecret().isBlank()) {
                exist.setSecret(cfg.getSecret());
                dirty = true;
            }
            if (dirty) {
                mediaServerService.updateById(exist);
                log.info("[流媒体] 默认节点缺字段已回填 serverId={}", cfg.getServerId());
            }
            return;
        }
        GbMediaServer server = new GbMediaServer();
        server.setServerId(cfg.getServerId());
        server.setName(cfg.getName());
        server.setHostIp(cfg.getHostIp());
        server.setHttpPort(cfg.getHttpPort());
        server.setSecret(cfg.getSecret());
        server.setStreamIp(cfg.getStreamIp() == null || cfg.getStreamIp().isBlank()
                ? cfg.getHostIp() : cfg.getStreamIp());
        server.setStreamHttpPort(cfg.getStreamHttpPort());
        server.setRtspPort(cfg.getRtspPort());
        server.setRtcPort(cfg.getRtcPort());
        server.setRtpPortStart(cfg.getRtpPortStart());
        server.setRtpPortEnd(cfg.getRtpPortEnd());
        server.setStatus(0);
        mediaServerService.save(server);
        log.info("[流媒体] 默认 ZLM 节点已写入 serverId={} {}:{}",
                cfg.getServerId(), cfg.getHostIp(), cfg.getHttpPort());
    }
}
