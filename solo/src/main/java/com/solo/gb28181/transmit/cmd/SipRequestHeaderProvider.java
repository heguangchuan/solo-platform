package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.InvalidArgumentException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.SubjectHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
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
     * {@link SipLayer} 用 {@code @Lazy} 注入，打破观察者模式天然的循环依赖：
     * <pre>
     *   SipLayer → SipProcessorObserver → List&lt;SipResponseProcessor&gt; →
     *   InviteResponseProcessor → SipRequestHeaderProvider → SipLayer
     * </pre>
     * 与 {@link com.solo.gb28181.sip.SipSender} 处理方式一致。
     */
    public SipRequestHeaderProvider(Gb28181Properties properties, @Lazy SipLayer sipLayer) {
        this.properties = properties;
        this.sipLayer = sipLayer;
    }

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
        String sipIp = properties.resolveSipIp(localIp);
        ViaHeader via = HEADER_FACTORY.createViaHeader(sipIp, properties.getPort(), transport, SipUtils.newViaTag());
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

    // ============================================================
    // INVITE / ACK / BYE  (S2.2 实时点播)
    // ============================================================

    /**
     * 构造发往设备的 INVITE 请求（实时点播 / 回放共用）
     *
     * @param device       目标设备
     * @param channelId    通道国标 ID（写入 Request-URI / To / Subject）
     * @param sdpBody      SDP 报文体（由 {@link SdpBuilder} 生成）
     * @param ssrc         10 位 SSRC（写入 Subject）
     * @param callId       调用方持有的 Call-Id
     * @param fromTag      调用方持有的 from-tag
     */
    public Request buildInviteRequest(GbDevice device, String channelId, String sdpBody,
                                      String ssrc, CallIdHeader callId, String fromTag)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();

        // Request-URI: sip:<channelId>@<deviceHostAddress>
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(channelId, device.getHostAddress());

        // Via
        List<ViaHeader> viaHeaders = new ArrayList<>();
        String sipIp = properties.resolveSipIp(localIp);
        ViaHeader via = HEADER_FACTORY.createViaHeader(sipIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        // From: sip:<platformId>@<platformDomain>;tag=<fromTag>
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(properties.getId(), properties.getDomain());
        Address fromAddr = ADDRESS_FACTORY.createAddress(fromUri);
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(fromAddr, fromTag);

        // To: sip:<channelId>@<deviceHostAddress>
        SipURI toUri = ADDRESS_FACTORY.createSipURI(channelId, device.getHostAddress());
        Address toAddr = ADDRESS_FACTORY.createAddress(toUri);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(toAddr, null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.INVITE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.INVITE, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        // Contact: sip:<platformId>@<localIp:platformPort>
        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), sipIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        request.addHeader(contact);

        // Subject: <channelId>:<ssrc>,<platformId>:0
        SubjectHeader subject = HEADER_FACTORY.createSubjectHeader(
                channelId + ":" + ssrc + "," + properties.getId() + ":0");
        request.addHeader(subject);

        // SDP body
        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "SDP");
        request.setContent(sdpBody, ct);
        return request;
    }

    /**
     * 构造发给设备的 ACK 请求（对 INVITE 200 OK 的确认）
     * <p>Request-URI 取响应 SDP 中 {@code o=} username + 收包源地址；
     * CallId / From / To 全部沿用 200 OK 响应里的，仅 CSeq 升到 ACK 方法。</p>
     */
    public Request buildAckRequest(GbDevice device, SIPResponse response, String remoteHost, int remotePort)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();

        FromHeader from = (FromHeader) response.getHeader(FromHeader.NAME);
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        CallIdHeader callId = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        CSeqHeader respCseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        long cseqNumber = respCseq == null ? cseqSeed.incrementAndGet() : respCseq.getSeqNumber();

        // Request-URI 优先用 SDP o= 行的 username（设备 ID） + 收包源 host:port；username 取不到时退回 To 头 user
        byte[] raw = response.getRawContent();
        String username = SdpBuilder.extractOriginUser(raw == null ? null : new String(raw));
        if (username == null) {
            URI toUri = to.getAddress().getURI();
            if (toUri instanceof SipURI sip) {
                username = sip.getUser();
            }
        }
        if (username == null || username.isBlank()) {
            username = device.getDeviceId();
        }
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(username, remoteHost + ":" + remotePort);

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqNumber, Request.ACK);

        Request ack = MESSAGE_FACTORY.createRequest(
                requestUri, Request.ACK, callId, cseq, from, to, viaHeaders, maxForwards);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), localIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        ack.addHeader(contact);
        return ack;
    }

    /**
     * 构造发给设备的 BYE 请求（结束一次 INVITE Dialog）
     */
    public Request buildByeRequest(GbDevice device, GbInviteSession session)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(session.getChannelId(), device.getHostAddress());

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        SipURI fromUri = ADDRESS_FACTORY.createSipURI(properties.getId(), properties.getDomain());
        Address fromAddr = ADDRESS_FACTORY.createAddress(fromUri);
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(fromAddr, session.getFromTag());

        SipURI toUri = ADDRESS_FACTORY.createSipURI(session.getChannelId(), device.getHostAddress());
        Address toAddr = ADDRESS_FACTORY.createAddress(toUri);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(toAddr, session.getToTag());

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CallIdHeader callId = HEADER_FACTORY.createCallIdHeader(session.getCallId());
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.BYE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.BYE, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), localIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        request.addHeader(contact);
        return request;
    }

    /**
     * 构造发给设备的 INFO 请求（Dialog 内控制，回放 PAUSE/PLAY/SCALE 用）
     * <p>Dialog 内请求复用 session 的 callId / from-tag / to-tag，仅 Via 用新 branch、CSeq 递增。
     * Body 是 {@code application/MANSRTSP} 文本（由 {@link RtspControlBuilder} 构造）。</p>
     */
    public Request buildInfoRequest(GbDevice device, GbInviteSession session, String rtspBody)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(session.getChannelId(), device.getHostAddress());

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        SipURI fromUri = ADDRESS_FACTORY.createSipURI(properties.getId(), properties.getDomain());
        Address fromAddr = ADDRESS_FACTORY.createAddress(fromUri);
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(fromAddr, session.getFromTag());

        SipURI toUri = ADDRESS_FACTORY.createSipURI(session.getChannelId(), device.getHostAddress());
        Address toAddr = ADDRESS_FACTORY.createAddress(toUri);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(toAddr, session.getToTag());

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CallIdHeader callId = HEADER_FACTORY.createCallIdHeader(session.getCallId());
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.INFO);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.INFO, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), localIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        request.addHeader(contact);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "MANSRTSP");
        request.setContent(rtspBody, ct);
        return request;
    }

    /**
     * 收到设备的 BYE 后，给设备回 200 OK 响应
     */
    public Response buildResponse(int statusCode, Request request) throws ParseException {
        return MESSAGE_FACTORY.createResponse(statusCode, request);
    }
}
