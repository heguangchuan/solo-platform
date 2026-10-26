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

    /** 模式：单向喊话（默认） */
    public static final String MODE_BROADCAST = "broadcast";
    /** 模式：双向对讲（平台↔设备都要传音频） */
    public static final String MODE_TALK = "talk";

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

    @Schema(description = "模式：broadcast=单向喊话，talk=双向对讲")
    private String mode;

    // ===== 对讲反向腿（设备→平台音频）专用字段 =====
    @Schema(description = "反向收流 stream 名（设备推上来的音频，app=rtp，WebRTC play 用）")
    private String recvStreamId;

    @Schema(description = "反向收流所在 ZLM 节点 ID")
    private String recvServerId;

    @Schema(description = "反向收流端口（ZLM openRtpServer 分配的，写进 200 OK 的 m=audio 行）")
    private Integer recvPort;

    @Schema(description = "反向收流 SSRC（10 位，写进 200 OK 的 y= 行）")
    private String recvSsrc;

    // ===== 级联喊话专用：本平台向上级(平台)收音频的那一路 Dialog 信息 =====
    @Schema(description = "级联喊话时，向上级发 INVITE 收音频的 Call-ID")
    private String parentCallId;

    @Schema(description = "级联喊话时，向上级发 INVITE 的 From-Tag")
    private String parentFromTag;

    @Schema(description = "级联喊话时，上级回 200 OK 的 To-Tag")
    private String parentToTag;

    @Schema(description = "级联喊话时对应的上级平台 id（GbParentPlatform.id），清场时用来给上级发 BYE")
    private String parentPlatformId;

    @Schema(description = "设备收流 IP（设备 SDP c= 行）")
    private String dstIp;

    @Schema(description = "设备收流端口（设备 SDP m=audio 行）")
    private Integer dstPort;

    @Schema(description = "SSRC（优先取设备 SDP y=，无则用本平台分配，用于 startSendRtp 与回复 SDP y= 行）")
    private String ssrc;

    @Schema(description = "发给设备的音频载荷类型：8=裸 PCMA，96=PS，按设备 INVITE SDP 选定")
    private Integer audioPt;

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

    /** 是否双向对讲模式 */
    public boolean isTalk() {
        return MODE_TALK.equalsIgnoreCase(mode);
    }
}
