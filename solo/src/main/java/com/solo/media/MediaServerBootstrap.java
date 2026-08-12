package com.solo.media;

import com.solo.media.properties.MediaProperties;
import com.solo.media.entity.MediaServer;
import com.solo.media.service.MediaServerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时根据 application.yaml 中 {@code media.default-server.*} 自动 upsert
 * 一个默认 ZLM 节点。
 * <p>这样部署只需启动 ZLM + solo 即可完成对接：solo 启动后默认节点已就绪，
 * ZLM hook 进来即可匹配。生产环境一般通过管理面再手工调整 hostIp / streamIp 等字段。</p>
 *
 * <p><b>ZLM 的全局配置（hook URL、api.secret、rtp_proxy.port_range 等）不通过本类推送</b>，
 * 完全由 ZLM 自身的 {@code config.ini} 负责。Solo 不调用 {@code setServerConfig}，
 * 因为：(1) ZLM 进程级配置应当由 ZLM 自身的配置文件管理，solo 越界修改会与
 * config.ini 漂移；(2) 与某参考实现不同的是，部署时 config.ini 已经按需
 * 配置完整（包括所有 hook URL 都指向 solo 的 {@code /api/hook} 路径），
 * 没必要在 solo 启动时重复推一遍。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaServerBootstrap {

    private final MediaProperties mediaProperties;
    private final MediaServerService mediaServerService;

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
        MediaServer exist = mediaServerService.lambdaQuery().eq(MediaServer::getServerId, cfg.getServerId()).one();
        if (exist != null) {
            // 已存在则以配置为准整体更新，保留 id / status / hookAliveTime 等运行期状态
            applyConfig(exist, cfg);
            mediaServerService.updateById(exist);
            log.info("[流媒体] 默认 ZLM 节点已更新 serverId={} {}:{}",
                    cfg.getServerId(), cfg.getHostIp(), cfg.getHttpPort());
            return;
        }
        MediaServer server = new MediaServer();
        applyConfig(server, cfg);
        server.setStatus(0);
        mediaServerService.save(server);
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
        server.setRtpPortStart(cfg.getRtpPortStart());
        server.setRtpPortEnd(cfg.getRtpPortEnd());
    }
}
