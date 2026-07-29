package com.solo.gb28181.sip.log;

import gov.nist.core.StackLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import java.util.Properties;

/**
 * JAIN-SIP StackLogger 的 SLF4J 适配
 * <p>替代 log4j-over-slf4j 桥接：JAIN-SIP 通过 {@code gov.nist.javax.sip.STACK_LOGGER} 属性注入本实现，
 * 所有协议栈内部日志都直接桥接到 SLF4J（项目用 Logback），caller location 准确，无额外依赖。</p>
 *
 * <p><strong>日志级别由 SLF4J 掌控</strong>：JAIN-SIP 打每条日志前先问 {@link #isLoggingEnabled(int)}，
 * 本实现据此委派到 {@code gov.nist} 这个 SLF4J logger 的对应级别，让协议栈按配置短路——既不产出噪音，
 * 也不浪费 CPU 拼日志串。想彻底静音把 {@code logging.level.gov.nist} 调到 warn/off 即可（默认 warn，
 * 仍保留真正的 SIP 错误/告警）。之前无条件返回 true 会让协议栈把全部 debug/trace/收发摘要灌进日志。</p>
 */
public class Slf4jStackLogger implements StackLogger {

    /** 完全限定类名，用于 SLF4J 定位调用位置 */
    private static final String FQCN = Slf4jStackLogger.class.getName();

    /** 级别开关判定用的固定 logger：受 {@code logging.level.gov.nist} 统一控制 */
    private static final Logger GATE = LoggerFactory.getLogger("gov.nist");

    /** 从调用栈中取出 JAIN-SIP 内部真正发起 log 调用的类作为 Logger 名 */
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

    /**
     * 按 JAIN-SIP 的 trace 级别委派到 SLF4J 级别开关，让协议栈自行短路。
     * JAIN-SIP trace 常量：TRACE_NONE=0 &lt; TRACE_MESSAGES=16 &lt; TRACE_EXCEPTION=17
     * &lt; TRACE_DEBUG=32；数字越大越细。
     */
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
