package com.solo.gb28181.sip;

import gov.nist.javax.sip.SipProviderImpl;
import com.solo.gb28181.sip.log.SipMessageLogger;
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
 * SIP 出站统一入口
 * <p>所有响应回复/主动请求发送都走这里，作用：</p>
 * <ul>
 *   <li>多 IP 监听场景下找到正确的 Provider 回包</li>
 *   <li>根据消息 Via 头自动选择 UDP/TCP</li>
 *   <li>需要等响应时注册 {@link SipSubscribeEvent}</li>
 * </ul>
 * <p>{@link SipLayer} 用 {@code @Lazy} 注入，打破
 * {@code SipLayer → SipProcessorObserver → SipRequestProcessor → SipSender → SipLayer} 循环依赖。</p>
 */
@Slf4j
@Component
public class SipSender {

    private final SipLayer sipLayer;
    private final SipSubscribe sipSubscribe;
    private final SipMessageLogger sipMessageLogger;

    public SipSender(@Lazy SipLayer sipLayer, SipSubscribe sipSubscribe, SipMessageLogger sipMessageLogger) {
        this.sipLayer = sipLayer;
        this.sipSubscribe = sipSubscribe;
        this.sipMessageLogger = sipMessageLogger;
    }

    /** 不等响应 */
    public void send(String localIp, Message message) throws SipException, ParseException {
        send(localIp, message, null, null, 0);
    }

    /**
     * 发送 SIP 消息并按需注册响应回调
     *
     * @param localIp   本机出口 IP（多网卡时决定走哪个 Provider）
     * @param message   要发送的 Request 或 Response
     * @param onOk      2xx 回调（可空）
     * @param onError   非 2xx 回调（可空）
     * @param timeoutMs 订阅超时（毫秒，0 = 用默认 10s）
     */
    public void send(String localIp, Message message,
                     Consumer<ResponseEvent> onOk,
                     Consumer<ResponseEvent> onError,
                     long timeoutMs) throws SipException, ParseException {
        send(localIp, message, onOk, onError, null, timeoutMs);
    }

    /**
     * 发送 SIP 消息并按需注册响应回调（含超时）
     *
     * @param onTimeout 超时回调（可空，仅当 onOk/onError 非空时有效）
     */
    public void send(String localIp, Message message,
                     Consumer<ResponseEvent> onOk,
                     Consumer<ResponseEvent> onError,
                     Runnable onTimeout,
                     long timeoutMs) throws SipException, ParseException {

        String transport = "UDP";
        ViaHeader via = (ViaHeader) message.getHeader(ViaHeader.NAME);
        if (via != null) transport = via.getTransport();

        // 注册响应订阅
        if (onOk != null || onError != null) {
            CallIdHeader cid = (CallIdHeader) message.getHeader(CallIdHeader.NAME);
            javax.sip.header.CSeqHeader cseq = (javax.sip.header.CSeqHeader) message.getHeader(javax.sip.header.CSeqHeader.NAME);
            if (cid != null && cseq != null) {
                String key = cid.getCallId() + cseq.getSeqNumber();
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
        sipMessageLogger.logOutbound(message);
        if (message instanceof Request req) {
            provider.sendRequest(req);
        } else if (message instanceof Response resp) {
            provider.sendResponse(resp);
        }
    }

    /** 生成全新 Call-Id */
    public CallIdHeader newCallId(String localIp, String transport) {
        SipProviderImpl provider = "TCP".equalsIgnoreCase(transport)
                ? sipLayer.getTcpSipProvider(localIp)
                : sipLayer.getUdpSipProvider(localIp);
        if (provider == null) provider = sipLayer.getUdpSipProvider();
        return provider == null ? null : provider.getNewCallId();
    }
}
