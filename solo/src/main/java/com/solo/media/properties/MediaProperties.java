package com.solo.media.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "media")
public class MediaProperties {

    // 必须和 ZLM config.ini 的 [api].secret 对上
    private String hookSecret;

    // 等不到 on_stream_changed 就算点播失败，后面会关端口、还 SSRC
    private int inviteTimeoutSeconds = 30;

    // 有些没录像能力的设备根本不回包，这里别设太长，超时就按无录像返回
    private int recordInfoTimeoutSeconds = 10;

    private int playbackTimeoutSeconds = 30;

    // 0=视频，1=音频；点播用视频段
    private int ssrcDomainStart = 0;

    // 带有效 wt 访问不存在的国标流时，自动给设备发 INVITE
    private boolean autoApplyPlay = true;

    // 关闭后任何客户端都能推流，生产环境别开这个口子
    private boolean pushAuthEnabled = false;

    private DefaultServer defaultServer = new DefaultServer();

    private PlayAuth playAuth = new PlayAuth();

    private ViewRecord viewRecord = new ViewRecord();

    @Data
    public static class DefaultServer {
        private boolean enabled = true;

        // 必须和 ZLM config.ini 的 [general].mediaServerId 完全一致，否则 hook 回来找不到节点
        private String serverId = "zlmediakit-local";
        private String name = "默认节点";
        private String hostIp = "127.0.0.1";
        private int httpPort = 8088;
        private String secret;

        // 对外播放地址用的 IP，留空跟 hostIp 走
        private String streamIp;

        // 0 表示跟 httpPort 走
        private int streamHttpPort = 0;

        // 0 表示不生成 WebRTC 地址
        private int rtcPort = 0;
    }

    @Data
    public static class PlayAuth {
        // 留空就用 jwt.secret，多实例记得配成一样的
        private String secret;

        // 没配就暂时沿用旧的 view-record.token-ttl-seconds
        private Long tokenTtlSeconds;
    }

    @Data
    public static class ViewRecord {
        // 只管观看记录落不落库，不影响播放鉴权
        private boolean enabled = true;

        // 老配置，新的用 media.play-auth.token-ttl-seconds
        @Deprecated
        private long tokenTtlSeconds = 7200;

        // 观看中超过这么多分钟还没闭合，就兜底关掉
        private int sweepStaleMinutes = 240;
    }
}
