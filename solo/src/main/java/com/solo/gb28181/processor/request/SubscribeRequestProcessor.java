package com.solo.gb28181.processor.request;

import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.subscribe.PlatformCatalogSubscribeManager;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.header.ExpiresHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * 接收上级平台的目录订阅（SUBSCRIBE）请求
 * <p>Event: Catalog，Expires>0 订阅/续期，Expires=0 退订。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscribeRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    private static final String LOG_TAG = "[级联目录订阅]";

    private final PlatformCatalogSubscribeManager subscribeManager;

    @Override
    public String getMethod() {
        return Request.SUBSCRIBE;
    }

    @Override
    public void process(RequestEvent event) {
        SIPRequest request = (SIPRequest) event.getRequest();
        String fromUser = SipUtils.getUserIdFromFromHeader(request);
        String toUser = SipUtils.getUserIdFromToHeader(request);

        String eventType = getEventHeader(request);
        if (!"Catalog".equalsIgnoreCase(eventType)) {
            log.info("{} 非 Catalog 订阅，回 403 from={} event={}", LOG_TAG, fromUser, eventType);
            safeResponse(request, Response.FORBIDDEN, "unsupported event");
            return;
        }

        int expires = getExpires(request);
        if (expires > 0) {
            subscribeManager.onSubscribe(fromUser, toUser, request, expires);
        } else {
            subscribeManager.onUnsubscribe(fromUser, toUser, request);
        }

        try {
            responseAck(request, Response.OK);
        } catch (Exception e) {
            log.warn("{} 回 200 失败 from={}: {}", LOG_TAG, fromUser, e.getMessage());
        }
    }

    private String getEventHeader(SIPRequest request) {
        javax.sip.header.EventHeader h = (javax.sip.header.EventHeader) request.getHeader(javax.sip.header.EventHeader.NAME);
        return h == null ? "" : h.getEventType();
    }

    private int getExpires(SIPRequest request) {
        ExpiresHeader h = (ExpiresHeader) request.getHeader(ExpiresHeader.NAME);
        return h == null ? 0 : h.getExpires();
    }

    private void safeResponse(SIPRequest request, int statusCode, String reason) {
        try {
            responseAck(request, statusCode, reason);
        } catch (Exception e) {
            log.warn("{} 回 {} 失败 reason={} : {}", LOG_TAG, statusCode, reason, e.getMessage());
        }
    }
}
