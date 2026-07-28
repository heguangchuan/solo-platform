package com.solo.gb28181.processor.request;

import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.service.impl.CascadeSendService;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;

/**
 * 入站 ACK 请求处理器。
 *
 * <p>ACK 不需要应答。当前唯一用途：上级点播场景（S6.b-3）本平台回 200 OK 后，上级回 ACK，
 * 此时对 UDP / TCP 被动会话真正触发 {@code startSendRtp} 把流推给上级
 * （TCP 主动会话已在回 200 时 {@code startSendRtpPassive} 开监听，幂等跳过）。</p>
 *
 * <p>本平台作为主叫（点播设备）时，ACK 由本平台主动发出，不会走到本 processor。</p>
 */
@Slf4j
@Component
public class AckRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    /** @Lazy 破环，同 InviteRequestProcessor */
    @Autowired
    @Lazy
    private CascadeSendService cascadeSendService;

    @Override
    public String getMethod() {
        return Request.ACK;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SIPRequest request = (SIPRequest) event.getRequest();
        CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        String callId = callIdHeader == null ? null : callIdHeader.getCallId();
        if (callId == null) {
            return;
        }
        // 仅级联推流会话关心 ACK；非级联的 ACK 查不到会话直接忽略
        cascadeSendService.onUpstreamAck(callId);
    }
}
