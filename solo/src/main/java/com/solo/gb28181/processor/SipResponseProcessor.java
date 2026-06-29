package com.solo.gb28181.processor;

import javax.sip.ResponseEvent;

/**
 * SIP 响应处理器
 * 每个实现负责一种被本平台主动发起的 SIP 方法对应的响应
 */
public interface SipResponseProcessor {

    /**
     * 该处理器负责的 SIP 方法名（与本平台发出的请求方法对应）
     */
    String getMethod();

    /**
     * 处理一次 SIP 响应事件
     */
    void process(ResponseEvent event) throws Exception;
}
