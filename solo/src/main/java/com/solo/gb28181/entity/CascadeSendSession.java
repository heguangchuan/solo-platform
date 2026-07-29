package com.solo.gb28181.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 级联推流会话（<strong>Redis 主存</strong>，纯运行时状态）
 *
 * <p>场景：本平台作为下级挂在上级平台下，上级发 INVITE 点播本平台共享的某通道。
 * 本平台先向真实下级设备取流（一路 {@code app=rtp/srcStream} 进 ZLM），再用 ZLM
 * {@code startSendRtp} 把这路流转推给上级给出的 {@code dstIp:dstPort:ssrc}。本会话记录这条推流腿。</p>
 *
 * <p>与设备点播会话 {@link GbInviteSession} 物理隔离：那条是"取流腿"（本平台→下级），
 * 本条是"推流腿"（本平台→上级）。两者通过 {@link #srcStream}（= 取流会话的 streamId）关联。</p>
 *
 * <p>业务主键 {@link #sendStreamId}；callId / srcStream 也建反查索引
 * （见 {@link com.solo.gb28181.service.impl.CascadeSendSessionServiceImpl}）。</p>
 */
@Data
public class CascadeSendSession {

    /** 回 200 OK 后等待上级 ACK */
    public static final String STATUS_WAIT_ACK = "WAIT_ACK";
    /** 已调 startSendRtp，推流中 */
    public static final String STATUS_SENDING = "SENDING";
    /** 已停止 */
    public static final String STATUS_STOPPED = "STOPPED";

    /** 业务主键：本推流会话唯一标识（{@code send_<callId hash>} 或直接用上级 callId 归一） */
    private String sendStreamId;

    /** ZLM 节点 ID（源流所在节点，也是发流节点） */
    private String serverId;

    /** 上级平台主键（gb_parent_platform.id） */
    private Long platformDbId;

    /** 上级平台国标编号（server_gb_id，= 上级 INVITE 的 From-user） */
    private String platformServerGbId;

    /** 本平台对该上级使用的国标编号（device_gb_id，写回复 SDP o= 行 / To-user） */
    private String localGbId;

    /** 被点播的通道国标编号（20 位，= 上级点播用的 exposed_code，写回复 SDP / 上级信令交互用） */
    private String channelId;

    /** 本平台真实通道国标编号（排查日志用；取流走 deviceId + 此码，不是 exposed_code） */
    private String localChannelId;

    /** 该通道所属真实设备国标 ID（取流用） */
    private String deviceId;

    /** 源流 app（固定 rtp） */
    private String srcApp;

    /** 源流 stream 名（= 取流会话 GbInviteSession.streamId，通常 deviceId_channelId） */
    private String srcStream;

    /** 上级收流 IP（上级 SDP c= 行） */
    private String dstIp;

    /** 上级收流端口（上级 SDP m= 行） */
    private Integer dstPort;

    /** 最终 SSRC（取上级 SDP y= 值），用于 startSendRtp 与回复 SDP y= 行 */
    private String ssrc;

    /** 本地发送端口（UDP/TCP 主动去连时分配；TCP 被动时为 ZLM 监听端口） */
    private Integer localSendPort;

    /** 传输语义（站在上级视角）：UDP / TCP-passive(上级等本平台去连) / TCP-active(上级来连本平台) */
    private String transport;

    /** 上级 INVITE 的 Call-Id / from-tag / to-tag（本平台在回 200 时生成 to-tag） */
    private String callId;
    private String fromTag;
    private String toTag;

    /** WAIT_ACK / SENDING / STOPPED */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
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
