package com.solo.gb28181.sip.parser;

import gov.nist.javax.sip.parser.MessageParser;
import gov.nist.javax.sip.parser.MessageParserFactory;
import gov.nist.javax.sip.stack.SIPTransactionStack;

/**
 * GB 解析器工厂
 * <p>解析器无状态，整个协议栈共享一个实例</p>
 * <p>修复参考：https://github.com/RestComm/jain-sip/issues/92</p>
 */
public class GbStringMsgParserFactory implements MessageParserFactory {

    private static final GbStringMsgParser INSTANCE = new GbStringMsgParser();

    @Override
    public MessageParser createMessageParser(SIPTransactionStack stack) {
        return INSTANCE;
    }
}
