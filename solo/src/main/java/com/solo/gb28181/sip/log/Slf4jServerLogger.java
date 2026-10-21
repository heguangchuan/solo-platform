package com.solo.gb28181.sip.log;

import gov.nist.core.CommonLogger;
import gov.nist.core.ServerLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import javax.sip.SipStack;
import java.util.Properties;

/**
 * <p>本类必须保留：JAIN-SIP 启动时无条件通过 {@code gov.nist.javax.sip.SERVER_LOGGER} 属性
 * 反射加载，漏设即回落到 {@code StackLoggerImpl}（依赖 log4j 1.x）抛
 * {@code NoClassDefFoundError: org/apache/log4j/Priority}，见 ADR-001。</p>
 *
 * <p>报文全文打印功能已废弃（ADR-033）：本类所有 logMessage 重载均为 no-op，
 * 排查 SIP 报文走 tcpdump / wireshark 等专业工具，不再走日志。</p>
 *
 * <p>注意：本类必须有公共无参构造，由 JAIN-SIP 反射创建。</p>
 */
public class Slf4jServerLogger implements ServerLogger {

    private StackLogger stackLogger;

    @Override
    public void closeLogFile() { /* no-op */ }

    @Override
    public void logMessage(SIPMessage message, String from, String to, boolean sender, long time) { /* no-op */ }

    @Override
    public void logMessage(SIPMessage message, String from, String to, String status, boolean sender, long time) { /* no-op */ }

    @Override
    public void logMessage(SIPMessage message, String from, String to, String status, boolean sender) { /* no-op */ }

    @Override
    public void logException(Exception ex) {
        if (stackLogger != null) {
            stackLogger.logException(ex);
        }
    }

    @Override
    public void setStackProperties(Properties stackProperties) { /* no-op */ }

    @Override
    public void setSipStack(SipStack sipStack) {
        if (sipStack instanceof SIPTransactionStack) {
            this.stackLogger = CommonLogger.getLogger(SIPTransactionStack.class);
        }
    }
}
