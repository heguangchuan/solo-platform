package com.solo.gb28181.processor.response;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.processor.SipResponseProcessor;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * INVITE 响应处理器
 * <p>本平台主动发出 INVITE 后，设备会以 100/180/200 响应。此处只关心 2xx：
 * <ol>
 *     <li>解析 SDP 中的 {@code o=} 用户名 + {@code y=} SSRC</li>
 *     <li>构造 ACK 并立即回发（GB28181 要求 INVITE 100% 必须 ACK）</li>
 *     <li>把 to-tag 持久化到 {@link GbInviteSession}，BYE 时需要复用</li>
 * </ol>
 * <p>业务上的"流就绪"由 ZLM hook {@code on_stream_changed regist=true} 触发 PlayService，
 * 不在本 processor 中处理。</p>
 */
@Slf4j
@Component
public class InviteResponseProcessor implements SipResponseProcessor {

    @Autowired
    private SipSender sipSender;
    /**
     * 用 @Lazy 打破 InviteResponseProcessor → SipLayer → SipProcessorObserver →
     * List&lt;SipResponseProcessor&gt; → InviteResponseProcessor 循环。
     * （与 {@link SipSender} 注入 SipLayer 同样的处理方式。）
     */
    @Autowired
    @Lazy
    private SipLayer sipLayer;
    @Autowired
    private SipRequestHeaderProvider headerProvider;
    @Autowired
    private GbInviteSessionService inviteSessionService;
    /** 设备查表：按 ADR-037 原则直接走 mapper，不绕 service。 */
    @Autowired
    private GbDeviceMapper deviceMapper;

    /**
     * 用 @Lazy 打破 InviteResponseProcessor → GbPlayService → SipCommander →
     * SipSender → SipLayer → SipProcessorObserver → InviteResponseProcessor 循环。
     */
    @Autowired
    @Lazy
    private GbPlayService playService;

    @Autowired
    @Lazy
    private GbPlaybackService playbackService;

    @Override
    public String getMethod() {
        return Request.INVITE;
    }

    @Override
    public void process(ResponseEvent event) throws Exception {
        SIPResponse response = (SIPResponse) event.getResponse();
        int status = response.getStatusCode();
        if (status < Response.OK || status >= Response.MULTIPLE_CHOICES) {
            return;
        }

        CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        if (callIdHeader == null) {
            log.warn("[实时点播] 收到 INVITE {} 响应但无 Call-Id", status);
            return;
        }
        String callId = callIdHeader.getCallId();

        GbInviteSession session = inviteSessionService.getByCallId(callId);
        if (session == null) {
            log.warn("[实时点播] 收到 INVITE {} 响应，但 callId 无对应会话: {}", status, callId);
            return;
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, session.getDeviceId()));
        if (device == null) {
            log.warn("[实时点播] 收到 INVITE {} 响应，但设备已离线: deviceId={}", status, session.getDeviceId());
            return;
        }

        // 1) 持久化 to-tag
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        String toTag = to == null ? null : to.getTag();
        if (toTag != null && (session.getToTag() == null || !toTag.equals(session.getToTag()))) {
            session.setToTag(toTag);
            inviteSessionService.update(session);
        }

        // 2) 解析 SDP 中的 y= SSRC（设备实际使用的 SSRC，可能与本平台分配的不一致）
        byte[] raw = response.getRawContent();
        String sdp = raw == null ? null : new String(raw);
        String remoteSsrc = SdpBuilder.extractSsrc(sdp);

        // 3) 构造并发送 ACK（必须在 SSRC 自适应前发，让设备先稳定 Dialog）
        String remoteHost = response.getPeerPacketSourceAddress().getHostAddress();
        int remotePort = response.getPeerPacketSourcePort();
        Request ack = headerProvider.buildAckRequest(device, response, remoteHost, remotePort);
        sipSender.send(sipLayer.getLocalIp(device.getLocalIp()), ack);
        log.info("[实时点播] 收到 200 OK 自动发送 ACK stream={} callId={}", session.getStreamId(), callId);

        // 4) SSRC 自适应：若设备自报 SSRC 与本地不一致，改写 ZLM 过滤值（失败则 BYE）。按 sessionType 路由。
        if (GbInviteSession.TYPE_PLAYBACK.equals(session.getSessionType())
                || GbInviteSession.TYPE_DOWNLOAD.equals(session.getSessionType())) {
            playbackService.onInviteOk(callId, remoteSsrc);
        } else {
            playService.onInviteOk(callId, remoteSsrc);
        }
    }
}
