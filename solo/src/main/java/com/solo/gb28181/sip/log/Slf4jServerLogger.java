package com.solo.gb28181.sip.log;

import gov.nist.core.CommonLogger;
import gov.nist.core.ServerLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import javax.sip.SipStack;
import java.util.Properties;

// 这个类不能删：JAIN-SIP 会反射建它，缺了会去摸 log4j 1.x 并抛 NoClassDefFoundError。
// 公共无参构造也不能动。报文全文不再打日志，要抓包就用 tcpdump/Wireshark。
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
