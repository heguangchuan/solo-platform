package com.solo.gb28181.processor;

import javax.sip.ResponseEvent;

public interface SipResponseProcessor {

    String getMethod();

    void process(ResponseEvent event) throws Exception;
}
