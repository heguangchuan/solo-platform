package com.solo.gb28181.processor.request;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;

/**
 * JAIN-SIP 工厂单例持有者
 * <p>HeaderFactory / MessageFactory / AddressFactory 都是无状态的，全局共用</p>
 */
final class SipFactoryHolder {

    private static final HeaderFactory HEADER_FACTORY;
    private static final MessageFactory MESSAGE_FACTORY;
    private static final AddressFactory ADDRESS_FACTORY;

    static {
        try {
            SipFactory sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");
            HEADER_FACTORY = sipFactory.createHeaderFactory();
            MESSAGE_FACTORY = sipFactory.createMessageFactory();
            ADDRESS_FACTORY = sipFactory.createAddressFactory();
        } catch (PeerUnavailableException e) {
            throw new IllegalStateException("Init SIP factories failed", e);
        }
    }

    private SipFactoryHolder() {}

    static HeaderFactory headerFactory() { return HEADER_FACTORY; }
    static MessageFactory messageFactory() { return MESSAGE_FACTORY; }
    static AddressFactory addressFactory() { return ADDRESS_FACTORY; }
}
