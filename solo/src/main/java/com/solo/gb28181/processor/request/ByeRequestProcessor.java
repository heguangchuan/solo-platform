package com.solo.gb28181.processor.request;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.service.GbPlaybackService;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * 设备主动 BYE 请求处理器
 * <p>设备掉电、被远端踢、自身判定无观看人后会主动 BYE。本平台必须立即回 200 OK，
 * 然后按会话类型走清扫路径（点播 → GbPlayService、回放 → GbPlaybackService）。</p>
 *
 * <h3>注意</h3>
 * <p>对于本平台 <strong>主动</strong> 发出的 BYE，对端会回 200 OK，
 * 走 {@link com.solo.gb28181.sip.SipSubscribe} 订阅回调，不会走到本 processor。</p>
 */
@Slf4j
@Component
public class ByeRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    @Autowired
    private GbInviteSessionService inviteSessionService;

    /** 用 @Lazy 打破循环依赖（PlayService → SipCommander → SipProcessorObserver → BYE processor → PlayService） */
    @Autowired
    @Lazy
    private GbPlayService playService;

    @Autowired
    @Lazy
    private GbPlaybackService playbackService;

    @Override
    public String getMethod() {
        return Request.BYE;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SIPRequest request = (SIPRequest) event.getRequest();
        // 1) 立即回 200 OK，避免设备 BYE 重传
        responseAck(request, Response.OK);

        // 2) 按 callId 查会话
        CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        String callId = callIdHeader == null ? null : callIdHeader.getCallId();
        if (callId == null) {
            log.warn("[设备 BYE] 缺 Call-Id");
            return;
        }
        GbInviteSession session = inviteSessionService.getByCallId(callId);
        if (session == null) {
            log.info("[设备 BYE] callId={} 无对应会话（可能已被清扫）", callId);
            return;
        }
        log.info("[设备 BYE] stream={} callId={} sessionType={}",
                session.getStreamId(), callId, session.getSessionType());
        if (GbInviteSession.TYPE_PLAYBACK.equals(session.getSessionType())
                || GbInviteSession.TYPE_DOWNLOAD.equals(session.getSessionType())) {
            playbackService.onDeviceBye(session);
        } else {
            playService.onDeviceBye(session);
        }
    }
}
