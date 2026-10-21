package com.solo.gb28181.processor;

import javax.sip.RequestEvent;

/**
 * 每个实现负责一种 SIP 方法（REGISTER / MESSAGE / INVITE / ACK / BYE / INFO / SUBSCRIBE / NOTIFY / CANCEL）
 */
public interface SipRequestProcessor {

    String getMethod();

    void process(RequestEvent event) throws Exception;
}
