package com.solo.gb28181.sip.log;

import gov.nist.core.CommonLogger;
import gov.nist.core.ServerLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import com.solo.gb28181.utils.XmlUtils;

import javax.sip.SipStack;
import java.util.Properties;

/**
 * JAIN-SIP ServerLogger 适配
 * <p>负责 SIP 收发报文级别的日志。通过 {@code gov.nist.javax.sip.SERVER_LOGGER} 属性注入。</p>
 * <p>注意：本类必须有公共无参构造，由 JAIN-SIP 反射创建。</p>
 */
public class Slf4jServerLogger implements ServerLogger {

    private StackLogger stackLogger;

    @Override
    public void closeLogFile() { /* no-op */ }

    @Override
    public void logMessage(SIPMessage message, String from, String to, boolean sender, long time) {
        if (stackLogger == null) return;
        stackLogger.logInfo(format(message, from, to, null, sender));
    }

    @Override
    public void logMessage(SIPMessage message, String from, String to, String status, boolean sender, long time) {
        if (stackLogger == null) return;
        stackLogger.logInfo(format(message, from, to, status, sender));
    }

    @Override
    public void logMessage(SIPMessage message, String from, String to, String status, boolean sender) {
        if (stackLogger == null) return;
        stackLogger.logInfo(format(message, from, to, status, sender));
    }

    @Override
    public void logException(Exception ex) {
        if (stackLogger == null) return;
        stackLogger.logException(ex);
    }

    @Override
    public void setStackProperties(Properties stackProperties) { /* no-op */ }

    @Override
    public void setSipStack(SipStack sipStack) {
        if (sipStack instanceof SIPTransactionStack) {
            this.stackLogger = CommonLogger.getLogger(SIPTransactionStack.class);
        }
    }

    private String format(SIPMessage message, String from, String to, String status, boolean sender) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(sender ? "[SIP-OUT] → " + from : "[SIP-IN] ← " + to);
        if (status != null) sb.append(" status=").append(status);
        // message.toString() 会用 JVM 默认编码拼报文体，海康 GB18030 中文会乱码；
        // 头部是 ASCII 不受影响，仅报文体按真实编码重新解码后拼接。
        sb.append('\n').append(headersOf(message));
        byte[] body = message.getRawContent();
        if (body != null && body.length > 0) {
            sb.append(XmlUtils.decodeBody(body));
        }
        return sb.toString();
    }

    /** 取 message 的头部字符串（不含报文体），避免 toString() 用默认编码渲染体 */
    private String headersOf(SIPMessage message) {
        // SIPMessage 没有单独导出头部的公共方法，这里用 toString() 截到空行（头体分隔）为止
        String full = message.toString();
        int sep = full.indexOf("\r\n\r\n");
        if (sep < 0) sep = full.indexOf("\n\n");
        return sep < 0 ? full : full.substring(0, sep + (full.charAt(sep) == '\r' ? 4 : 2));
    }
}
