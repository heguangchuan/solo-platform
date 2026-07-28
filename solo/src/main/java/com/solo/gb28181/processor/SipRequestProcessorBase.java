package com.solo.gb28181.processor;

import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.utils.SipUtils;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sip.InvalidArgumentException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.address.SipURI;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;

/**
 * 请求处理器抽象基类
 * <p>提供 SIP 响应构造和 XML 报文解析的通用方法，所有 RequestProcessor 继承本类。</p>
 */
@Slf4j
public abstract class SipRequestProcessorBase {

    @Autowired
    protected SipSender sipSender;

    protected static final HeaderFactory HEADER_FACTORY;
    protected static final MessageFactory MESSAGE_FACTORY;

    static {
        try {
            SipFactory f = SipFactory.getInstance();
            f.setPathName("gov.nist");
            HEADER_FACTORY = f.createHeaderFactory();
            MESSAGE_FACTORY = f.createMessageFactory();
        } catch (PeerUnavailableException e) {
            throw new IllegalStateException("Init SIP factories failed", e);
        }
    }

    /** 回复简单状态码（无 body） */
    protected SIPResponse responseAck(SIPRequest request, int statusCode)
            throws SipException, ParseException {
        return responseAck(request, statusCode, null);
    }

    /** 回复状态码 + Reason Phrase */
    protected SIPResponse responseAck(SIPRequest request, int statusCode, String reason)
            throws SipException, ParseException {
        // 仅 final 响应（>=200）才补 To-tag 建立 Dialog；provisional（如 100 Trying）不加，避免误建对话
        if (statusCode >= 200 && request.getToHeader().getTag() == null) {
            request.getToHeader().setTag(SipUtils.newToTag());
        }
        SIPResponse response = (SIPResponse) MESSAGE_FACTORY.createResponse(statusCode, request);
        response.setStatusCode(statusCode);
        if (reason != null) response.setReasonPhrase(reason);
        sipSender.send(request.getLocalAddress().getHostAddress(), response);
        return response;
    }

    /** 回复 200 OK 携带 XML body（MANSCDP+xml） */
    protected SIPResponse responseXmlAck(SIPRequest request, String xml)
            throws SipException, ParseException, InvalidArgumentException {
        if (request.getToHeader().getTag() == null) {
            request.getToHeader().setTag(SipUtils.newToTag());
        }
        SIPResponse response = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.OK, request);
        ContentTypeHeader cth = HEADER_FACTORY.createContentTypeHeader("Application", "MANSCDP+xml");
        response.setContent(xml, cth);
        sipSender.send(request.getLocalAddress().getHostAddress(), response);
        return response;
    }

    /** 解析 MESSAGE 请求体 XML，返回根元素；空或失败返回 null */
    protected Element getRootElement(Request request) {
        return XmlUtils.parseRoot(request.getRawContent());
    }
}
