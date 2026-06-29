package com.solo.gb28181.sip;

import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipResponseProcessor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SIP 事件统一观察者
 * <p>实现 {@link SipListener}，把 JAIN-SIP 抛出的所有事件按方法名分发到注册的 Processor 上。</p>
 * <p>请求处理器实现 {@link SipRequestProcessor}，按 {@code getMethod()} 注册（REGISTER/MESSAGE/INVITE…）。</p>
 * <p>响应处理器实现 {@link SipResponseProcessor}，按方法名+状态码段（1xx/2xx/4xx/5xx）注册。</p>
 */
@Slf4j
@Component
public class SipProcessorObserver implements SipListener {

    @Autowired(required = false)
    private List<SipRequestProcessor> requestProcessors;

    @Autowired(required = false)
    private List<SipResponseProcessor> responseProcessors;

    private final Map<String, SipRequestProcessor> requestMap = new HashMap<>();
    private final Map<String, SipResponseProcessor> responseMap = new HashMap<>();

    private SipProvider udpProvider;
    private SipProvider tcpProvider;

    @PostConstruct
    public void init() {
        if (requestProcessors != null) {
            for (SipRequestProcessor p : requestProcessors) {
                requestMap.put(p.getMethod().toUpperCase(), p);
                log.info("[GB28181] 注册请求处理器: {} -> {}", p.getMethod(), p.getClass().getSimpleName());
            }
        }
        if (responseProcessors != null) {
            for (SipResponseProcessor p : responseProcessors) {
                responseMap.put(p.getMethod().toUpperCase(), p);
                log.info("[GB28181] 注册响应处理器: {} -> {}", p.getMethod(), p.getClass().getSimpleName());
            }
        }
    }

    /**
     * 由 {@link SipLayer} 在创建完 Provider 后回填
     */
    public void bindProviders(SipProvider udp, SipProvider tcp) {
        this.udpProvider = udp;
        this.tcpProvider = tcp;
    }

    public SipProvider getUdpProvider() { return udpProvider; }
    public SipProvider getTcpProvider() { return tcpProvider; }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        SipRequestProcessor processor = requestMap.get(method.toUpperCase());
        if (processor == null) {
            log.warn("[GB28181] 未注册的 SIP 请求方法: {}", method);
            return;
        }
        try {
            processor.process(requestEvent);
        } catch (Exception e) {
            log.error("[GB28181] 处理请求 {} 异常", method, e);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        String method = Optional.ofNullable(responseEvent.getClientTransaction())
                .map(t -> t.getRequest().getMethod())
                .orElse("UNKNOWN");
        SipResponseProcessor processor = responseMap.get(method.toUpperCase());
        if (processor == null) {
            log.debug("[GB28181] 未处理的响应: method={} status={}", method, response.getStatusCode());
            return;
        }
        try {
            processor.process(responseEvent);
        } catch (Exception e) {
            log.error("[GB28181] 处理响应 {} status={} 异常", method, response.getStatusCode(), e);
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("[GB28181] 事务超时: {}", timeoutEvent.isServerTransaction() ? "server" : "client");
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.warn("[GB28181] SIP IO 异常: host={} port={} transport={}",
                exceptionEvent.getHost(), exceptionEvent.getPort(), exceptionEvent.getTransport());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent event) {
        // 不输出日志，过于频繁
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent event) {
        // 不输出日志，过于频繁
    }
}
