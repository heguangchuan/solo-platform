package com.solo.gb28181.sip;

import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipResponseProcessor;
import com.solo.gb28181.sip.log.SipMessageLogger;
import gov.nist.javax.sip.message.SIPResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SIP 事件统一观察者
 * <p>请求按方法名分发到 {@link SipRequestProcessor}；响应除按方法名分发外，
 * 还按 (CallId+CSeq) 匹配回调到 {@link SipSubscribe}（主动发起请求等响应）。</p>
 * <p>处理逻辑放到 {@code taskExecutor} 异步线程池，避免阻塞 SIP listener 线程。</p>
 */
@Slf4j
@Component
public class SipProcessorObserver implements SipListener {

    @Autowired(required = false)
    private List<SipRequestProcessor> requestProcessors;

    @Autowired(required = false)
    private List<SipResponseProcessor> responseProcessors;

    @Autowired
    private SipSubscribe sipSubscribe;

    @Autowired
    private SipMessageLogger sipMessageLogger;

    private final Map<String, SipRequestProcessor> requestMap = new HashMap<>();
    private final Map<String, SipResponseProcessor> responseMap = new HashMap<>();

    @PostConstruct
    public void init() {
        if (requestProcessors != null) {
            for (SipRequestProcessor p : requestProcessors) {
                requestMap.put(p.getMethod().toUpperCase(), p);
                log.info("[消息分发] 注册请求处理器: {} -> {}", p.getMethod(), p.getClass().getSimpleName());
            }
        }
        if (responseProcessors != null) {
            for (SipResponseProcessor p : responseProcessors) {
                responseMap.put(p.getMethod().toUpperCase(), p);
                log.info("[消息分发] 注册响应处理器: {} -> {}", p.getMethod(), p.getClass().getSimpleName());
            }
        }
    }

    @Override
    @Async("taskExecutor")
    public void processRequest(RequestEvent requestEvent) {
        String method = requestEvent.getRequest().getMethod();
        sipMessageLogger.logInbound(requestEvent.getRequest());
        SipRequestProcessor processor = requestMap.get(method.toUpperCase());
        if (processor == null) {
            log.warn("[消息分发] 未注册的 SIP 请求方法: {}", method);
            return;
        }
        try {
            processor.process(requestEvent);
        } catch (Exception e) {
            log.error("[消息分发] 处理请求 {} 异常", method, e);
        }
    }

    @Override
    @Async("taskExecutor")
    public void processResponse(ResponseEvent responseEvent) {
        SIPResponse response = (SIPResponse) responseEvent.getResponse();
        int status = response.getStatusCode();
        CallIdHeader callIdHeader = response.getCallIdHeader();
        CSeqHeader cSeqHeader = response.getCSeqHeader();
        String method = cSeqHeader == null ? null : cSeqHeader.getMethod();

        // 报文日志必须在 1xx return 之前打，否则 100 Trying 永远看不到
        sipMessageLogger.logInbound(response);

        // 1xx 不处理（180 Ringing 等）
        if (status >= Response.TRYING && status < Response.OK) {
            return;
        }

        // 按方法名分发给响应处理器
        if (method != null) {
            SipResponseProcessor processor = responseMap.get(method.toUpperCase());
            if (processor != null) {
                try {
                    processor.process(responseEvent);
                } catch (Exception e) {
                    log.error("[消息分发] 处理响应 {} status={} 异常", method, status, e);
                }
            }
        }

        // 按 CallId+CSeq 触发订阅回调
        if (callIdHeader != null && cSeqHeader != null) {
            String key = callIdHeader.getCallId() + cSeqHeader.getSeqNumber();
            SipSubscribeEvent event = sipSubscribe.get(key);
            if (event != null) {
                boolean isOk = (status >= Response.OK && status < Response.MULTIPLE_CHOICES)
                        || status == Response.UNAUTHORIZED;
                try {
                    if (isOk && event.getOnOk() != null) {
                        event.getOnOk().accept(responseEvent);
                    } else if (!isOk && event.getOnError() != null) {
                        event.getOnError().accept(responseEvent);
                    }
                } catch (Exception e) {
                    log.error("[响应订阅] 回调异常 key={}", key, e);
                } finally {
                    sipSubscribe.remove(key);
                }
            }
        }

        if (status >= Response.BAD_REQUEST && responseEvent.getDialog() != null) {
            responseEvent.getDialog().delete();
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("[消息分发] 事务超时: server={}", timeoutEvent.isServerTransaction());
    }

    @Override
    public void processIOException(IOExceptionEvent e) {
        log.warn("[消息分发] SIP IO 异常: host={} port={} transport={}", e.getHost(), e.getPort(), e.getTransport());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent event) { /* 不打印 */ }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent event) { /* 不打印 */ }
}
