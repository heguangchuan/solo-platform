package com.solo.gb28181.processor.request;

import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.service.impl.CascadeSendService;
import com.solo.gb28181.service.impl.GbBroadcastService;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;

/**
 * <p>ACK 不需要应答。两类入站 INVITE 的 200 OK 被对端 ACK 后，都在这里真正触发推流：</p>
 * <ol>
 *   <li>上级点播（S6.b-3）：上级回 ACK，对 UDP / TCP 被动会话 startSendRtp 把流推给上级；</li>
 *   <li>语音广播：设备回 ACK，对 UDP / TCP 被动会话 startSendRtp 把浏览器音频推给设备。</li>
 * </ol>
 * <p>TCP 主动会话已在回 200 时 startSendRtpPassive 开监听，service 内幂等跳过。
 * 本平台作为主叫（点播设备）时，ACK 由本平台主动发出，不会走到本 processor。</p>
 */
@Slf4j
@Component
public class AckRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    /** @Lazy 破环，同 InviteRequestProcessor */
    @Autowired
    @Lazy
    private CascadeSendService cascadeSendService;

    /** 语音广播：设备对广播 INVITE 200 的 ACK，此时 startSendRtp 推音频 */
    @Autowired
    @Lazy
    private GbBroadcastService gbBroadcastService;

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
        // 级联推流 / 语音广播各自按 callId 查会话，查不到直接忽略
        cascadeSendService.onUpstreamAck(callId);
        gbBroadcastService.onDeviceAck(callId);
    }
}
