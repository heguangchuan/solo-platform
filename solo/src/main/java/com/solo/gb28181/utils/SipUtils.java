package com.solo.gb28181.utils;

import gov.nist.javax.sip.message.SIPRequest;
import lombok.experimental.UtilityClass;

import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderAddress;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;

/**
 * SIP 报文常用字段提取工具
 */
@UtilityClass
public class SipUtils {

    /**
     * 取 From 头里的 SIP 用户名（GB28181 中即设备 ID 或平台 ID）
     */
    public static String getUserFromFromHeader(Request request) {
        FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
        return getUser(from);
    }

    /**
     * 取 To 头里的 SIP 用户名
     */
    public static String getUserFromToHeader(Request request) {
        ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
        return getUser(to);
    }

    private static String getUser(HeaderAddress header) {
        if (header == null || header.getAddress() == null || header.getAddress().getURI() == null) {
            return null;
        }
        if (header.getAddress().getURI() instanceof SipURI sipURI) {
            return sipURI.getUser();
        }
        return null;
    }

    /**
     * 取请求来源 IP（优先 Via 字段中的发送方信息，回落到底层 SIPRequest 的 remoteAddress）
     */
    public static String getRemoteIp(Request request) {
        if (request instanceof SIPRequest sipRequest && sipRequest.getRemoteAddress() != null) {
            return sipRequest.getRemoteAddress().getHostAddress();
        }
        return null;
    }

    /**
     * 取请求来源端口
     */
    public static Integer getRemotePort(Request request) {
        if (request instanceof SIPRequest sipRequest) {
            return sipRequest.getRemotePort();
        }
        return null;
    }

    /**
     * 取请求传输协议（UDP/TCP）
     */
    public static String getTransport(Request request) {
        if (request instanceof SIPRequest sipRequest) {
            return sipRequest.getTopmostViaHeader() == null ? "UDP"
                    : sipRequest.getTopmostViaHeader().getTransport();
        }
        return "UDP";
    }
}
