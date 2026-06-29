package com.solo.gb28181.processor;

import javax.sip.RequestEvent;

/**
 * SIP 请求处理器
 * 每个实现负责一种 SIP 方法（REGISTER / MESSAGE / INVITE / ACK / BYE / INFO / SUBSCRIBE / NOTIFY / CANCEL）
 */
public interface SipRequestProcessor {

    /**
     * 该处理器负责的 SIP 方法名
     */
    String getMethod();

    /**
     * 处理一次 SIP 请求事件
     */
    void process(RequestEvent event) throws Exception;
}
