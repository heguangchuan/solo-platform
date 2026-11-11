package com.solo.media.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <p>注意 ZLM 是独立部署的（Docker），本服务通过两条通道与之交互：</p>
 * <ul>
 *     <li>ZLM REST API（出站）：openRtpServer / closeRtpServer / getMediaList 等</li>
 *     <li>ZLM hook 回调（入站）：on_publish / on_stream_changed / on_server_keepalive 等</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "media")
public class MediaProperties {

    /**
     * ZLM hook 回调的鉴权 secret（须与 ZLM config.ini 中 [api].secret 一致）；
     * solo 收到 hook 后会校验请求中的 secret 字段。
     */
    private String hookSecret;

    /**
     * 单次 INVITE 等待 ZLM on_stream_changed 的超时（秒）；
     * 超时未就绪则视为点播失败、自动 closeRtpServer + 释放 SSRC。
     */
    private int inviteTimeoutSeconds = 30;

    /**
     * RecordInfo 录像查询分片合并超时（秒）；超时未收齐 SumNum 条则视为查询失败。
     * <p>设备无录像能力时通常完全不回 Response，超时即按"无录像"返回空列表，
     * 故这里设短一些（10s），避免用户干等太久。</p>
     */
    private int recordInfoTimeoutSeconds = 10;

    /**
     * 单次 PLAYBACK INVITE 等待流就绪的超时（秒）。
     */
    private int playbackTimeoutSeconds = 30;

    /**
     * SSRC 池默认域类型：0=视频，1=音频；正常点播一律走视频段。
     */
    private int ssrcDomainStart = 0;

    /**
     * 自动拉流（autoApplyPlay）：开启后，当用户通过播放URL访问一个不存在的流时，
     * 自动发送 INVITE 给设备开始推流，用户不需要先点"播放"按钮。
     */
    private boolean autoApplyPlay = true;

    /**
     * 推流鉴权是否启用。true=on_publish 时校验 URL 里的 ?token= 参数是否命中
     * stream_push_client 且 enabled=1；未命中或未传 token 一律拒绝。
     * false（默认）=不做任何校验，任何客户端都能往 ZLM 推流（生产环境务必开启）。
     */
    private boolean pushAuthEnabled = false;

    /**
     * 启动时若 gb_media_server 表为空则自动写入的默认节点。
     */
    private DefaultServer defaultServer = new DefaultServer();

    /**
     * 观看记录（谁看了哪路流、看了多久）相关配置。
     */
    private ViewRecord viewRecord = new ViewRecord();

    /**
     * 默认 ZLM 节点定义；其 serverId 必须与 ZLM config.ini 中
     * [general].mediaServerId 完全一致，否则 hook 回来时找不到节点。
     */
    @Data
    public static class DefaultServer {
        private boolean enabled = true;
        private String serverId = "zlmediakit-local";
        private String name = "默认节点";
        private String hostIp = "127.0.0.1";
        private int httpPort = 8088;
        private String secret;
        /**
         * 对外播放使用的 IP（HTTP-FLV / WS-FLV / HLS / WebRTC）；留空则同 hostIp
         */
        private String streamIp;
        /**
         * 对外 HTTP/WS-FLV/HLS 端口；0 表示与 httpPort 一致
         */
        private int streamHttpPort = 0;
        /**
         * 对外 WebRTC 端口；0 表示不生成 WebRTC URL
         */
        private int rtcPort = 0;
    }

    /** 观看记录的开关和参数 */
    @Data
    public static class ViewRecord {
        /** 关了就不签令牌，on_play 认不出人，只跳过记录不拦播放 */
        private boolean enabled = true;

        /** 令牌有效期（秒），只管播放发起那一刻，看再久也不会中途失效 */
        private long tokenTtlSeconds = 7200;

        /** 清扫阈值（分钟）：观看中超过这么久还没闭合的，按兜底关掉 */
        private int sweepStaleMinutes = 240;
    }
}
