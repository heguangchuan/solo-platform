package com.solo.gb28181.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 流媒体（ZLMediaKit）相关配置，对应 application.yaml 中 gb28181.media.* 段。
 * <p>
 * 注意 ZLM 是独立部署的（Docker），本服务通过两条通道与之交互：
 * <ul>
 *     <li>ZLM REST API（出站）：openRtpServer / closeRtpServer / updateRtpServerSSRC 等</li>
 *     <li>ZLM hook 回调（入站）：on_publish / on_stream_changed / on_server_keepalive 等</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "gb28181.media")
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
     * 启动时若 gb_media_server 表为空则自动写入的默认节点。
     */
    private DefaultServer defaultServer = new DefaultServer();

    /**
     * 默认 ZLM 节点定义；其 serverId 必须与 ZLM config.ini 中
     * [general].mediaServerId 完全一致，否则 hook 回来时找不到节点。
     */
    @Data
    public static class DefaultServer {
        /**
         * 是否在启动时自动 upsert 默认节点
         */
        private boolean enabled = true;
        private String serverId = "zlmediakit-local";
        private String name = "默认节点";
        private String hostIp = "127.0.0.1";
        private int httpPort = 10005;
        private String secret;
        private int rtpPortStart = 30000;
        private int rtpPortEnd = 35000;
        /**
         * 对外播放使用的 IP（HTTP-FLV / WS-FLV / HLS / WebRTC）；留空则同 hostIp
         */
        private String streamIp;
        /**
         * 对外 HTTP/WS-FLV/HLS 端口；0 表示与 httpPort 一致
         */
        private int streamHttpPort = 0;
        /**
         * 对外 RTSP 端口；0 表示不生成 RTSP URL
         */
        private int rtspPort = 0;
        /**
         * 对外 WebRTC 端口；0 表示不生成 WebRTC URL
         */
        private int rtcPort = 0;
    }
}
