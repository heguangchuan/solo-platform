package com.solo.gb28181.sip.log;

import gov.nist.core.StackLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import java.util.Properties;

// JAIN-SIP 日志桥到 SLF4J；isLoggingEnabled 必须真看 gov.nist 的级别，不然 debug/trace 全灌进来。
// 想安静点配 logging.level.gov.nist=warn，真正的错误和告警还在。
public class Slf4jStackLogger implements StackLogger {

    private static final String FQCN = Slf4jStackLogger.class.getName();

    private static final Logger GATE = LoggerFactory.getLogger("gov.nist");

    private static LocationAwareLogger getLogger() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        // [0]=getLogger [1]=log [2]=logXxx [3]=JAIN-SIP caller
        String className = stack.length > 3 ? stack[3].getClassName() : FQCN;
        return (LocationAwareLogger) LoggerFactory.getLogger(className);
    }

    private static void log(int level, String message) {
        getLogger().log(null, FQCN, level, message, null, null);
    }

    private static void log(int level, String message, Throwable t) {
        getLogger().log(null, FQCN, level, message, null, t);
    }

    @Override public void logStackTrace() {}
    @Override public void logStackTrace(int traceLevel) {}
    @Override public int getLineCount() { return 0; }
    @Override public void logException(Throwable ex) { log(LocationAwareLogger.WARN_INT, ex.getMessage(), ex); }
    @Override public void logDebug(String message) { log(LocationAwareLogger.DEBUG_INT, message); }
    @Override public void logDebug(String message, Exception ex) { log(LocationAwareLogger.DEBUG_INT, message, ex); }
    @Override public void logTrace(String message) { log(LocationAwareLogger.TRACE_INT, message); }
    @Override public void logFatalError(String message) { log(LocationAwareLogger.ERROR_INT, message); }
    @Override public void logError(String message) { log(LocationAwareLogger.ERROR_INT, message); }
    @Override public void logError(String message, Exception ex) { log(LocationAwareLogger.ERROR_INT, message, ex); }
    @Override public void logWarning(String message) { log(LocationAwareLogger.WARN_INT, message); }
    @Override public void logInfo(String message) { log(LocationAwareLogger.INFO_INT, message); }
    @Override public boolean isLoggingEnabled() { return GATE.isInfoEnabled(); }

    // JAIN-SIP 的数字越大越细：16 消息、17 异常、32 调试。
    @Override
    public boolean isLoggingEnabled(int logLevel) {
        if (logLevel >= StackLogger.TRACE_DEBUG) return GATE.isDebugEnabled();
        if (logLevel >= StackLogger.TRACE_MESSAGES) return GATE.isInfoEnabled();
        return GATE.isErrorEnabled();
    }
    @Override public void disableLogging() {}
    @Override public void enableLogging() {}
    @Override public void setBuildTimeStamp(String buildTimeStamp) {}
    @Override public void setStackProperties(Properties stackProperties) {}
    @Override public String getLoggerName() { return FQCN; }
}
