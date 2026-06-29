package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.SipProviderImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sip.InvalidArgumentException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SIP 主动请求头构造器
 * <p>为 MESSAGE / INVITE / SUBSCRIBE / INFO / BYE / NOTIFY 等"本平台主动发起"的请求统一生成头部。</p>
 * <p>关键点：</p>
 * <ul>
 *   <li>Request-URI 用对端 deviceId/channelId @ 对端 hostAddress</li>
 *   <li>From 用本平台 ID @ 本平台 domain</li>
 *   <li>To 与 Request-URI 同 SipURI</li>
 *   <li>Via 上 setRPort() 让对端按收到包的端口回（NAT 友好）</li>
 *   <li>CSeq 单调递增（同一进程内全局序列）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SipRequestHeaderProvider {

    private static final AddressFactory ADDRESS_FACTORY;
    private static final HeaderFactory HEADER_FACTORY;
    private static final MessageFactory MESSAGE_FACTORY;

    static {
        try {
            SipFactory f = SipFactory.getInstance();
            f.setPathName("gov.nist");
            ADDRESS_FACTORY = f.createAddressFactory();
            HEADER_FACTORY = f.createHeaderFactory();
            MESSAGE_FACTORY = f.createMessageFactory();
        } catch (PeerUnavailableException e) {
            throw new IllegalStateException("Init SIP factories failed", e);
        }
    }

    private final Gb28181Properties properties;
    private final SipLayer sipLayer;

    /** 全局 CSeq 序列 */
    private final AtomicLong cseqSeed = new AtomicLong(System.currentTimeMillis() / 1000);

    /**
     * 构造一个发往设备的 MESSAGE 请求
     *
     * @param device     目标设备
     * @param xmlBody    XML 报文体（MANSCDP+xml）
     * @param callId     调用方提供的 Call-Id（不能为空）
     * @return Request
     */
    public Request buildMessageRequest(GbDevice device, String xmlBody, CallIdHeader callId)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();

        // Request-URI: sip:<deviceId>@<deviceHost:devicePort>
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(device.getDeviceId(), device.getHostAddress());

        // Via
        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        // From: sip:<platformId>@<platformDomain>
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(properties.getId(), properties.getDomain());
        Address fromAddr = ADDRESS_FACTORY.createAddress(fromUri);
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(fromAddr, SipUtils.newFromTag());

        // To: 与 Request-URI 同结构
        SipURI toUri = ADDRESS_FACTORY.createSipURI(device.getDeviceId(), device.getHostAddress());
        Address toAddr = ADDRESS_FACTORY.createAddress(toUri);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(toAddr, null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.MESSAGE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.MESSAGE, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "MANSCDP+xml");
        request.setContent(xmlBody, ct);
        return request;
    }

    /**
     * 用对端 transport 取一个新的 Call-Id
     */
    public CallIdHeader newCallId(GbDevice device) {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();
        SipProviderImpl provider = "TCP".equalsIgnoreCase(transport)
                ? sipLayer.getTcpSipProvider(localIp)
                : sipLayer.getUdpSipProvider(localIp);
        if (provider == null) provider = sipLayer.getUdpSipProvider();
        return provider == null ? null : provider.getNewCallId();
    }
}
