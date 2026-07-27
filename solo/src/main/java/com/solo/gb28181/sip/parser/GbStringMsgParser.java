package com.solo.gb28181.sip.parser;

import gov.nist.javax.sip.SIPConstants;
import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;

/**
 * 国标专用 SIP 消息解析器
 * <p>JAIN-SIP 默认按 ISO-8859-1 解析 SIP 行/头，遇到中文设备名/地址会乱码。
 * 本类强制按 UTF-8 解码所有 SIP 行/头，报文体（XML）仍按字节传递给业务层处理。</p>
 * <p>兼容 MESSAGE 体长度的特殊判断（部分国标设备的 Content-Length 与实际体长不一致，需按字节兜底）。</p>
 * <p>注：JAIN-SIP 1.3.0-91 的 {@link MessageParser} 接口只声明了 {@link #parseSIPMessage}
 * 一个方法，旧版本残留的 parseAddress/parseHost 等已不在接口中。</p>
 */
@Slf4j
public class GbStringMsgParser implements MessageParser {

    private static final boolean COMPUTE_CONTENT_LENGTH_FROM_MESSAGE = false;

    @Override
    public SIPMessage parseSIPMessage(byte[] msgBuffer, boolean readBody, boolean strict,
                                      ParseExceptionListener parseExceptionListener) throws ParseException {
        if (msgBuffer == null || msgBuffer.length == 0) return null;

        int i = 0;
        // 跳过开头的控制字符
        try {
            while (msgBuffer[i] < 0x20) i++;
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }

        String currentLine;
        String currentHeader = null;
        boolean isFirstLine = true;
        SIPMessage message = null;

        do {
            int lineStart = i;
            try {
                while (msgBuffer[i] != '\r' && msgBuffer[i] != '\n') i++;
            } catch (ArrayIndexOutOfBoundsException e) {
                break;
            }
            int lineLength = i - lineStart;

            currentLine = new String(msgBuffer, lineStart, lineLength, StandardCharsets.UTF_8);
            currentLine = trimEndOfLine(currentLine);

            if (currentLine.isEmpty()) {
                if (currentHeader != null) {
                    processHeader(currentHeader, message, parseExceptionListener, msgBuffer);
                }
            } else {
                if (isFirstLine) {
                    message = processFirstLine(currentLine, parseExceptionListener, msgBuffer);
                } else {
                    char firstChar = currentLine.charAt(0);
                    if (firstChar == '\t' || firstChar == ' ') {
                        // 头折行
                        if (currentHeader == null) throw new ParseException("Bad header continuation.", 0);
                        currentHeader += currentLine.substring(1);
                    } else {
                        if (currentHeader != null) {
                            processHeader(currentHeader, message, parseExceptionListener, msgBuffer);
                        }
                        currentHeader = currentLine;
                    }
                }
            }

            if (msgBuffer[i] == '\r' && msgBuffer.length > i + 1 && msgBuffer[i + 1] == '\n') i++;
            i++;
            isFirstLine = false;
        } while (!currentLine.isEmpty());

        if (message == null) throw new ParseException("Bad message", 0);
        message.setSize(i);

        // 报文体（XML）按字节透传给后续业务，不在此处做编码转换
        if (readBody && message.getContentLength() != null) {
            int contentLen = message.getContentLength().getContentLength();
            if (contentLen != 0) {
                int bodyLength = msgBuffer.length - i;
                byte[] body = new byte[bodyLength];
                System.arraycopy(msgBuffer, i, body, 0, bodyLength);
                message.setMessageContent(body, !strict, COMPUTE_CONTENT_LENGTH_FROM_MESSAGE, contentLen);
            } else if (message.getCSeqHeader() != null
                    && message.getCSeqHeader().getMethod().equalsIgnoreCase("MESSAGE")) {
                // GB28181 MESSAGE 报文体的 Content-Length 经常被错误地填 0，按实际长度兜底
                int bodyLength = msgBuffer.length - i;
                byte[] body = new byte[bodyLength];
                System.arraycopy(msgBuffer, i, body, 0, bodyLength);
                message.setMessageContent(body, !strict, COMPUTE_CONTENT_LENGTH_FROM_MESSAGE, bodyLength);
            } else if (!COMPUTE_CONTENT_LENGTH_FROM_MESSAGE && strict) {
                String last4 = new String(msgBuffer, msgBuffer.length - 4, 4);
                if (!"\r\n\r\n".equals(last4)) {
                    throw new ParseException("Extraneous characters at the end of the message ", i);
                }
            }
        }
        return message;
    }

    private static String trimEndOfLine(String line) {
        if (line == null) return null;
        int i = line.length() - 1;
        while (i >= 0 && line.charAt(i) <= 0x20) i--;
        if (i == line.length() - 1) return line;
        if (i == -1) return "";
        return line.substring(0, i + 1);
    }

    private SIPMessage processFirstLine(String firstLine, ParseExceptionListener listener, byte[] raw)
            throws ParseException {
        SIPMessage message;
        if (!firstLine.startsWith(SIPConstants.SIP_VERSION_STRING)) {
            message = new SIPRequest();
            try {
                RequestLine requestLine = new RequestLineParser(firstLine + "\n").parse();
                ((SIPRequest) message).setRequestLine(requestLine);
            } catch (ParseException ex) {
                if (listener != null) {
                    listener.handleException(ex, message, RequestLine.class, firstLine, new String(raw, StandardCharsets.UTF_8));
                } else {
                    throw ex;
                }
            }
        } else {
            message = new SIPResponse();
            try {
                StatusLine sl = new StatusLineParser(firstLine + "\n").parse();
                ((SIPResponse) message).setStatusLine(sl);
            } catch (ParseException ex) {
                if (listener != null) {
                    listener.handleException(ex, message, StatusLine.class, firstLine, new String(raw, StandardCharsets.UTF_8));
                } else {
                    throw ex;
                }
            }
        }
        return message;
    }

    private void processHeader(String header, SIPMessage message, ParseExceptionListener listener, byte[] raw)
            throws ParseException {
        if (header == null || header.isEmpty()) return;
        HeaderParser headerParser;
        try {
            headerParser = ParserFactory.createParser(header + "\n");
        } catch (ParseException ex) {
            if (listener != null) {
                listener.handleException(ex, message, null, header, null);
                return;
            }
            throw ex;
        }
        try {
            SIPHeader sipHeader = headerParser.parse();
            message.attachHeader(sipHeader, false);
        } catch (ParseException ex) {
            if (listener != null) {
                String headerName = Lexer.getHeaderName(header);
                Class<?> headerClass = NameMap.getClassFromName(headerName);
                if (headerClass == null) headerClass = ExtensionHeaderImpl.class;
                listener.handleException(ex, message, headerClass, header, new String(raw, StandardCharsets.UTF_8));
            }
        }
    }
}
