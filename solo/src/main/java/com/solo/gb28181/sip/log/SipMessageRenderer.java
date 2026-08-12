package com.solo.gb28181.sip.log;

import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPMessage;

/**
 * SIP 报文渲染：把 {@link SIPMessage} 拼成可读的报文全文（首行 + 头部 + 报文体）。
 * <p>{@link Slf4jServerLogger}（JAIN-SIP 侧）与 {@link SipMessageLogger}（业务场景侧）共用。</p>
 */
public final class SipMessageRenderer {

    private SipMessageRenderer() {
    }

    /**
     * 渲染报文全文。
     * <p>不用 {@code message.toString()} 整体渲染：它会用 JVM 默认编码拼报文体，海康 GB18030 中文会乱码。
     * 头部是 ASCII 不受影响，故头部沿用 toString()、报文体按真实编码单独解码后拼接。</p>
     */
    public static String render(SIPMessage message) {
        if (message == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(512);
        sb.append(headersOf(message));
        byte[] body = message.getRawContent();
        if (body != null && body.length > 0) {
            sb.append(XmlUtils.decodeBody(body));
        }
        return sb.toString();
    }

    /** 取 message 的首行 + 头部（不含报文体） */
    private static String headersOf(SIPMessage message) {
        // SIPMessage 没有单独导出头部的公共方法，这里用 toString() 截到空行（头体分隔）为止
        String full = message.toString();
        int sep = full.indexOf("\r\n\r\n");
        if (sep < 0) sep = full.indexOf("\n\n");
        return sep < 0 ? full : full.substring(0, sep + (full.charAt(sep) == '\r' ? 4 : 2));
    }
}
