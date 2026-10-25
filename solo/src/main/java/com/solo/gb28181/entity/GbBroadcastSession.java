package com.solo.gb28181.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "语音广播会话（Redis 主存）")
@Data
public class GbBroadcastSession {

    /** 已发 Broadcast 通知，等设备反向 INVITE */
    public static final String STATUS_WAIT_INVITE = "WAIT_INVITE";
    /** 已回 200 OK，等设备 ACK */
    public static final String STATUS_WAIT_ACK = "WAIT_ACK";
    /** 已调 startSendRtp，推流中 */
    public static final String STATUS_SENDING = "SENDING";
    /** 已停止 */
    public static final String STATUS_STOPPED = "STOPPED";

    @Schema(description = "广播会话唯一标识（= ZLM 推流 stream 名）")
    private String broadcastStreamId;

    @Schema(description = "ZLM 节点 ID")
    private String serverId;

    @Schema(description = "被喊话设备国标 ID")
    private String deviceId;

    @Schema(description = "放音通道国标编号（写 Broadcast 通知的 DeviceID）")
    private String channelId;

    @Schema(description = "源流 app（固定 broadcast）")
    private String srcApp;

    @Schema(description = "源流 stream 名（浏览器 WebRTC 推流名，= broadcastStreamId）")
    private String srcStream;

    @Schema(description = "设备收流 IP（设备 SDP c= 行）")
    private String dstIp;

    @Schema(description = "设备收流端口（设备 SDP m=audio 行）")
    private Integer dstPort;

    @Schema(description = "SSRC（本平台分配，用于 startSendRtp 与回复 SDP y= 行）")
    private String ssrc;

    @Schema(description = "本地发送端口（UDP/TCP 主动去连时分配；TCP 被动时为 ZLM 监听端口）")
    private Integer localSendPort;

    @Schema(description = "传输模式（站在设备视角）：UDP / TCP-passive（设备等平台去连） / TCP-active（设备来连平台）")
    private String transport;

    @Schema(description = "SIP Call-ID（设备反向 INVITE 的 Call-Id）")
    private String callId;

    @Schema(description = "SIP From-tag（设备 INVITE 带的 tag）")
    private String fromTag;

    @Schema(description = "SIP To-tag（本平台回 200 时生成）")
    private String toTag;

    @Schema(description = "会话状态 WAIT_INVITE/WAIT_ACK/SENDING/STOPPED")
    private String status;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "开始推流时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sendTime;

    /** 是否 TCP（含主动/被动）——便于 startSendRtp 传 is_udp */
    public boolean isTcp() {
        return "TCP-passive".equalsIgnoreCase(transport) || "TCP-active".equalsIgnoreCase(transport);
    }

    /** 设备是否主动来连平台（TCP-active）——此时平台走 startSendRtpPassive 开监听 */
    public boolean isDeviceActive() {
        return "TCP-active".equalsIgnoreCase(transport);
    }
}
