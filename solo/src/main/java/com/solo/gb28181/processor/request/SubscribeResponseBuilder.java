package com.solo.gb28181.processor.request;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.experimental.UtilityClass;

import javax.sip.InvalidArgumentException;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.message.Response;
import java.text.ParseException;

import static com.solo.gb28181.processor.SipRequestProcessorBase.HEADER_FACTORY;
import static com.solo.gb28181.processor.SipRequestProcessorBase.MESSAGE_FACTORY;

// SUBSCRIBE 的 200 必须带 Event/Expires/Subscription-State。
// JAIN-SIP 看到负 Expires 会抛，统一钳到 [0,86400]。
@UtilityClass
public class SubscribeResponseBuilder {

    public static SIPResponse buildSubscribeOk(SIPRequest request,
                                               String event,
                                               int expires,
                                               String bodyXml) throws ParseException, InvalidArgumentException {
        SIPResponse response = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.OK, request);
        addSubscribeHeaders(response, event, expires);
        if (bodyXml != null && !bodyXml.isEmpty()) {
            ContentTypeHeader cth = HEADER_FACTORY.createContentTypeHeader("Application", "MANSCDP+xml");
            response.setContent(bodyXml, cth);
        }
        return response;
    }

    public static SIPResponse buildTerminated(SIPRequest request,
                                              String event) throws ParseException, InvalidArgumentException {
        SIPResponse response = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.OK, request);
        addEventHeader(response, event);
        SubscriptionStateHeader ssh = HEADER_FACTORY.createSubscriptionStateHeader("terminated");
        // 退订无续期，Expires=0
        ExpiresHeader eh = HEADER_FACTORY.createExpiresHeader(0);
        response.addHeader(ssh);
        response.addHeader(eh);
        return response;
    }

    private static void addSubscribeHeaders(SIPResponse response, String event, int expires)
            throws ParseException, InvalidArgumentException {
        int safeExpires = clampExpires(expires);
        addEventHeader(response, event);
        SubscriptionStateHeader ssh = HEADER_FACTORY.createSubscriptionStateHeader("active");
        if (safeExpires > 0) {
            // setExpires 只接受正数，0 就别调了
            ssh.setExpires(safeExpires);
        }
        ExpiresHeader eh = HEADER_FACTORY.createExpiresHeader(safeExpires);
        response.addHeader(ssh);
        response.addHeader(eh);
    }

    private static int clampExpires(int expires) {
        if (expires < 0) {
            return 0;
        }
        if (expires > 86400) {
            return 86400;
        }
        return expires;
    }

    private static void addEventHeader(SIPResponse response, String event) throws ParseException {
        if (event == null || event.isBlank()) {
            return;
        }
        EventHeader eh = HEADER_FACTORY.createEventHeader(event.trim());
        response.addHeader(eh);
    }
}
