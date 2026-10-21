package com.solo.gb28181.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "级联推流会话（Redis 主存）")
@Data
public class CascadeSendSession {

    /** 回 200 OK 后等待上级 ACK */
    public static final String STATUS_WAIT_ACK = "WAIT_ACK";
    /** 已调 startSendRtp，推流中 */
    public static final String STATUS_SENDING = "SENDING";
    /** 已停止 */
    public static final String STATUS_STOPPED = "STOPPED";

    @Schema(description = "推流会话唯一标识（send_<callId hash> 或直接用上级 callId 归一）")
    private String sendStreamId;

    @Schema(description = "ZLM 节点 ID（源流所在节点，也是发流节点）")
    private String serverId;

    @Schema(description = "上级平台数据库主键（gb_parent_platform.id）")
    private Long platformDbId;

    @Schema(description = "上级平台国标编号（server_gb_id，= 上级 INVITE 的 From-user）")
    private String platformServerGbId;

    @Schema(description = "本平台对该上级使用的国标编号（device_gb_id，写回复 SDP o= 行 / To-user）")
    private String localGbId;

    @Schema(description = "被点播的通道国标编号（20 位，= 上级点播用的 exposed_code，写回复 SDP / 上级信令交互用）")
    private String channelId;

    @Schema(description = "本平台真实通道国标编号（排查日志用；取流走 deviceId + 此码，不是 exposed_code）")
    private String localChannelId;

    @Schema(description = "所属设备国标 ID（取流用）")
    private String deviceId;

    @Schema(description = "源流 app（固定 rtp）")
    private String srcApp;

    @Schema(description = "源流 stream 名（= 取流会话 GbInviteSession.streamId，通常 deviceId_channelId）")
    private String srcStream;

    @Schema(description = "上级收流 IP（上级 SDP c= 行）")
    private String dstIp;

    @Schema(description = "上级收流端口（上级 SDP m= 行）")
    private Integer dstPort;

    @Schema(description = "SSRC（取上级 SDP y= 值，用于 startSendRtp 与回复 SDP y= 行）")
    private String ssrc;

    @Schema(description = "本地发送端口（UDP/TCP 主动去连时分配；TCP 被动时为 ZLM 监听端口）")
    private Integer localSendPort;

    @Schema(description = "传输模式（站在上级视角）：UDP / TCP-passive（上级等本平台去连） / TCP-active（上级来连本平台）")
    private String transport;

    @Schema(description = "SIP Call-ID（上级 INVITE 的 Call-Id；本平台在回 200 时生成 to-tag，from-tag / to-tag 另存）")
    private String callId;

    @Schema(description = "SIP From-tag")
    private String fromTag;

    @Schema(description = "SIP To-tag")
    private String toTag;

    @Schema(description = "会话状态 WAIT_ACK/SENDING/STOPPED")
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

    /** 上级是否主动来连本平台（TCP-active）——此时本平台走 startSendRtpPassive 开监听 */
    public boolean isUpstreamActive() {
        return "TCP-active".equalsIgnoreCase(transport);
    }
}
