package com.solo.gb28181.sip;

import com.solo.gb28181.config.Gb28181Properties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import java.util.Properties;

/**
 * SIP 协议栈层
 * 启动 UDP / TCP 双栈监听，将 SipProvider 注入给 {@link SipProcessorObserver} 统一处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SipLayer {

    private final Gb28181Properties properties;
    private final SipProcessorObserver sipProcessorObserver;

    private SipStack sipStack;
    private SipProvider udpSipProvider;
    private SipProvider tcpSipProvider;

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("[GB28181] gb28181.enabled=false，跳过 SIP 协议栈启动");
            return;
        }
        try {
            startSipStack();
        } catch (Exception e) {
            log.error("[GB28181] SIP 协议栈启动失败", e);
        }
    }

    private void startSipStack() throws Exception {
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        Properties props = new Properties();
        props.setProperty("javax.sip.STACK_NAME", "solo-gb28181");
        // 自动维护对话（Dialog）
        props.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "ON");
        // 调试日志级别（生产可调为 OFF/16）
        props.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
        // 收到不带 Via 的请求时回复 400 而非抛异常
        props.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");
        // 允许处理大尺寸消息（Catalog 等可能很大）
        props.setProperty("gov.nist.javax.sip.RECEIVE_UDP_BUFFER_SIZE", String.valueOf(1024 * 1024));
        props.setProperty("gov.nist.javax.sip.SEND_UDP_BUFFER_SIZE", String.valueOf(1024 * 1024));

        sipStack = sipFactory.createSipStack(props);

        String listenIp = (properties.getIp() == null || properties.getIp().isBlank()) ? "0.0.0.0" : properties.getIp();
        int port = properties.getPort();

        // UDP
        ListeningPoint udpPoint = sipStack.createListeningPoint(listenIp, port, "UDP");
        udpSipProvider = sipStack.createSipProvider(udpPoint);
        udpSipProvider.addSipListener(sipProcessorObserver);

        // TCP
        ListeningPoint tcpPoint = sipStack.createListeningPoint(listenIp, port, "TCP");
        tcpSipProvider = sipStack.createSipProvider(tcpPoint);
        tcpSipProvider.addSipListener(sipProcessorObserver);

        sipProcessorObserver.bindProviders(udpSipProvider, tcpSipProvider);

        log.info("[GB28181] SIP 协议栈已启动 listen={}:{} (UDP+TCP) sipId={} domain={}",
                listenIp, port, properties.getId(), properties.getDomain());
    }

    @PreDestroy
    public void destroy() {
        try {
            if (sipStack != null) {
                sipStack.stop();
                log.info("[GB28181] SIP 协议栈已停止");
            }
        } catch (Exception e) {
            log.warn("[GB28181] SIP 协议栈停止异常", e);
        }
    }

    /**
     * 根据传输协议名取得对应 Provider（"UDP" / "TCP"）
     */
    public SipProvider getProvider(String transport) {
        if ("TCP".equalsIgnoreCase(transport)) {
            return tcpSipProvider;
        }
        return udpSipProvider;
    }

    /**
     * 默认 UDP Provider（最常用）
     */
    public SipProvider getUdpSipProvider() {
        return udpSipProvider;
    }

    public SipProvider getTcpSipProvider() {
        return tcpSipProvider;
    }
}
