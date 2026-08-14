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

/**
 * SUBSCRIBE 200 OK 响应构造器（ADR-029）。
 * <p>SUBSCRIBE 200 OK 区别于普通 200 OK：必须带 {@code Event}、{@code Expires}、{@code Subscription-State}
 * 三件套（RFC 6665 / GB/T 28181-2016 §9 硬性要求），
 * 不带这些头，故单独抽出本工具。</p>
 * <p>To-tag 由 {@code MESSAGE_FACTORY.createResponse(200, request)} 自动生成（取自 request To 头，无则新建），
 * 后续 NOTIFY 必须复用同一 to-tag 维持 dialog。</p>
 */
@UtilityClass
public class SubscribeResponseBuilder {

    /**
     * 构造目录订阅 OK 响应（含 Event/Subscription-State/Expires + MANSCDP+xml body）。
     *
     * @param request  收到的 SUBSCRIBE 请求
     * @param event    Event 头取值（catalog / mobilePosition / ...），与请求保持一致
     * @param expires  Expires 头取值（秒），与请求保持一致；0 = 退订（响应中以 0 表达）
     * @param bodyXml  MANSCDP+xml 响应体（{@code <Response><CmdType>...</CmdType>...</Response>}）
     */
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

    /**
     * 构造 SUBSCRIBE 退订（200 + Subscription-State: terminated）的响应。无 body。
     */
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

    /**
     * 给已构造好的 Response 追加 SUBSCRIBE 200 必带的三个头。
     * <p>expires 钳到 [0, 86400]：JAIN-SIP {@code HeaderFactoryImpl.createExpiresHeader(int)} 在
     * {@code expires < 0} 时抛 {@code InvalidArgumentException}（NIST impl 128-130），
     * 上游坏报文 / 编码 bug 都可能让 {@code resolveExpires} 算出负数。钳到 0 后走退订语义，
     * 比抛异常终止 SIP listener 线程安全得多。订阅表本身有 {@code MAX_EXPIRES_SECONDS=86400} 限制，
     * 这里同步保持以防调用方传超长值。</p>
     */
    private static void addSubscribeHeaders(SIPResponse response, String event, int expires)
            throws ParseException, InvalidArgumentException {
        int safeExpires = clampExpires(expires);
        addEventHeader(response, event);
        SubscriptionStateHeader ssh = HEADER_FACTORY.createSubscriptionStateHeader("active");
        if (safeExpires > 0) {
            // SubscriptionStateHeader.setExpires 在 expires<0 时抛 InvalidArgumentException
            // （spec 规定 MUST be > 0）；0 不调
            ssh.setExpires(safeExpires);
        }
        ExpiresHeader eh = HEADER_FACTORY.createExpiresHeader(safeExpires);
        response.addHeader(ssh);
        response.addHeader(eh);
    }

    /** 防御性钳位：负数 → 0，> 24h → 24h。 */
    private static int clampExpires(int expires) {
        if (expires < 0) return 0;
        if (expires > 86400) return 86400;
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
