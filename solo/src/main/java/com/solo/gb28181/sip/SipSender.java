package com.solo.gb28181.sip;

import gov.nist.javax.sip.SipProviderImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.function.Consumer;

/**
 * SIP 消息发送统一入口。
 *
 * 干嘛的：
 *  - 多 IP 场景下找对 Provider 回包（不然会发不出去）
 *  - 自动根据 Via 头选 UDP/TCP
 *  - 需要等响应的请求，注册到 SipSubscribe 里回调
 *
 * SipLayer 用 @Lazy 注入，不然会跟 SipProcessorObserver 形成循环依赖：
 *   SipLayer -> SipProcessorObserver -> SipRequestProcessor -> SipSender -> SipLayer
 *  走构造函数注入 + @Lazy 就能绕过去。
 */
@Slf4j
@Component
public class SipSender {

    private final SipLayer sipLayer;
    private final SipSubscribe sipSubscribe;

    public SipSender(@Lazy SipLayer sipLayer, SipSubscribe sipSubscribe) {
        this.sipLayer = sipLayer;
        this.sipSubscribe = sipSubscribe;
    }

    // fire-and-forget，发了就完事不等响应
    public void send(String localIp, Message message) throws SipException, ParseException {
        send(localIp, message, null, null, 0);
    }

    /**
     * 发 SIP 消息，需要等响应就传回调。
     *
     * @param localIp   本机出口 IP（多网卡决定走哪个 provider）
     * @param message   要发的 Request 或 Response
     * @param onOk      2xx 回调，可空
     * @param onError   非 2xx 回调，可空
     * @param timeoutMs 超时时间(ms)，0 默认 10s
     */
    public void send(String localIp, Message message,
                     Consumer<ResponseEvent> onOk,
                     Consumer<ResponseEvent> onError,
                     long timeoutMs) throws SipException, ParseException {
        send(localIp, message, onOk, onError, null, timeoutMs);
    }

    // 带超时回调的版本
    public void send(String localIp, Message message,
                     Consumer<ResponseEvent> onOk,
                     Consumer<ResponseEvent> onError,
                     Runnable onTimeout,
                     long timeoutMs) throws SipException, ParseException {

        String transport = "UDP";
        ViaHeader via = (ViaHeader) message.getHeader(ViaHeader.NAME);
        if (via != null) transport = via.getTransport();

        // 有回调的话先注册到订阅管理器，等响应回来对上
        if (onOk != null || onError != null) {
            CallIdHeader cid = (CallIdHeader) message.getHeader(CallIdHeader.NAME);
            javax.sip.header.CSeqHeader cseq = (javax.sip.header.CSeqHeader) message.getHeader(javax.sip.header.CSeqHeader.NAME);
            if (cid != null && cseq != null) {
                String key = cid.getCallId() + cseq.getSeqNumber();
                // TODO: 这个 key 以后可能会撞，callId 一样 + cseq 一样的情况虽然少，但不是没有，
                //  后面想想要不要加个本地生成的标识区分一下。先这样吧。
                SipSubscribeEvent ev = new SipSubscribeEvent(key, timeoutMs <= 0 ? 10_000L : timeoutMs);
                ev.setOnOk(onOk);
                ev.setOnError(onError);
                ev.setOnTimeout(onTimeout);
                sipSubscribe.add(ev);
            }
        }

        SipProviderImpl provider = "TCP".equalsIgnoreCase(transport)
                ? sipLayer.getTcpSipProvider(localIp)
                : sipLayer.getUdpSipProvider(localIp);
        if (provider == null) {
            log.error("[消息发送] 未找到 {} {} 的 Provider", transport, localIp);
            throw new SipException("no provider for " + transport + "/" + localIp);
        }
        if (message instanceof Request req) {
            provider.sendRequest(req);
        } else if (message instanceof Response resp) {
            provider.sendResponse(resp);
        }
    }

    // 生成一个新的 Call-Id
    public CallIdHeader newCallId(String localIp, String transport) {
        SipProviderImpl provider = "TCP".equalsIgnoreCase(transport)
                ? sipLayer.getTcpSipProvider(localIp)
                : sipLayer.getUdpSipProvider(localIp);
        if (provider == null) provider = sipLayer.getUdpSipProvider();
        return provider == null ? null : provider.getNewCallId();
    }
}
