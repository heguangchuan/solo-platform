package com.solo.gb28181.sip;

import java.util.Properties;

/**
 * <p>关键属性说明见每一项注释。</p>
 * <p>完整可选项见 gov.nist.javax.sip.SipStackImpl</p>
 */
public final class SipProperties {

    private SipProperties() {}

    /**
     * @param stackName 协议栈名（多实例隔离用）
     */
    public static Properties build(String stackName) {
        Properties p = new Properties();
        p.setProperty("javax.sip.STACK_NAME", stackName);

        // 关闭自动对话支持。开启后 INVITE/SUBSCRIBE 会被自动维护 Dialog 状态，
        // 国标场景下我们要自己控制 Dialog（INVITE 主动发起/被动响应分别处理），强行自动会带来状态混乱。
        p.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");

        // 接收所有 NOTIFY，即使没有先前的 SUBSCRIBE。上级平台/部分设备会发"无订阅 NOTIFY"，不开会丢报文。
        p.setProperty("gov.nist.javax.sip.DELIVER_UNSOLICITED_NOTIFY", "true");
        // 关闭自动对话错误处理（与 AUTOMATIC_DIALOG_SUPPORT=off 配合）
        p.setProperty("gov.nist.javax.sip.AUTOMATIC_DIALOG_ERROR_HANDLING", "false");
        // CANCEL 客户端事务时做一致性检查
        p.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "true");
        // 即使没有 Dialog 也派发 Terminated 事件（便于异步资源回收）
        p.setProperty("gov.nist.javax.sip.DELIVER_TERMINATED_EVENT_FOR_NULL_DIALOG", "true");
        // 自动按报文体长度计算 Content-Length，兼容设备 Content-Length 写错的情况
        p.setProperty("gov.nist.javax.sip.COMPUTE_CONTENT_LENGTH_FROM_MESSAGE_BODY", "true");
        // 引用释放策略
        p.setProperty("gov.nist.javax.sip.RELEASE_REFERENCES_STRATEGY", "Normal");
        // TCP keep-alive 超时
        p.setProperty("gov.nist.javax.sip.RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT", "60");
        // 监听器线程可重入（业务逻辑较重时必开，否则单线程串行会卡所有请求）
        p.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
        // 线程审计周期（毫秒）
        p.setProperty("gov.nist.javax.sip.THREAD_AUDIT_INTERVAL_IN_MILLISECS", "30000");
        // 使用 NIO 消息处理器，比默认阻塞 IO 在大量并发下表现明显更好
        p.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY",
                "gov.nist.javax.sip.stack.NioMessageProcessorFactory");

        // 必须始终设置 STACK_LOGGER / SERVER_LOGGER：JAIN-SIP 构造 SipStack 时无条件 Class.forName 加载
        // 这两个类，若不设置则回落到 gov.nist.javax.sip.stack.StackLoggerImpl（依赖 log4j 1.x），
        // Spring Boot 4 没有 log4j 1.x 会抛 NoClassDefFoundError。
        p.setProperty("gov.nist.javax.sip.STACK_LOGGER", "com.solo.gb28181.sip.log.Slf4jStackLogger");
        p.setProperty("gov.nist.javax.sip.SERVER_LOGGER", "com.solo.gb28181.sip.log.Slf4jServerLogger");
        // 报文全文日志一律关闭：JAIN-SIP 的 LOG_MESSAGE_CONTENT 打的是全量报文且依赖 gov.nist logger
        // 才能调出来；本项目侧也不再按业务场景打印（ADR-033 移除 SipMessageLogger）。
        // 排查 SIP 报文走 tcpdump / wireshark 等专业工具。
        p.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "false");
        return p;
    }
}
