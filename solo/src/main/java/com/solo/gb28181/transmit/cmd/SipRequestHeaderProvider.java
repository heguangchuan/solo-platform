package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPRequest;
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
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
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
@Component
// Via 都带 rport，NAT 后让对端按实际收包端口回。
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

    private final AtomicLong cseqSeed = new AtomicLong(System.currentTimeMillis() / 1000);

    // @Lazy 是特意加的：SipLayer -> 处理器 -> 这里 -> SipLayer，直接注入会成环。
    public SipRequestHeaderProvider(Gb28181Properties properties, @Lazy SipLayer sipLayer) {
        this.properties = properties;
        this.sipLayer = sipLayer;
    }

    public Request buildMessageRequest(GbDevice device, String xmlBody, CallIdHeader callId)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(device.getDeviceId(), device.getHostAddress());
        List<ViaHeader> viaHeaders = new ArrayList<>();
        String sipIp = properties.resolveSipIp(localIp);
        ViaHeader via = HEADER_FACTORY.createViaHeader(sipIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(properties.getId(), properties.getDomain());
        Address fromAddr = ADDRESS_FACTORY.createAddress(fromUri);
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(fromAddr, SipUtils.newFromTag());
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

    public CallIdHeader newCallId(GbDevice device) {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();
        SipProviderImpl provider = "TCP".equalsIgnoreCase(transport)
                ? sipLayer.getTcpSipProvider(localIp)
                : sipLayer.getUdpSipProvider(localIp);
        if (provider == null) provider = sipLayer.getUdpSipProvider();
        return provider == null ? null : provider.getNewCallId();
    }

    public Request buildInviteRequest(GbDevice device, String channelId, String sdpBody,
                                      String ssrc, CallIdHeader callId, String fromTag)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(channelId, device.getHostAddress());
        List<ViaHeader> viaHeaders = new ArrayList<>();
        String sipIp = properties.resolveSipIp(localIp);
        ViaHeader via = HEADER_FACTORY.createViaHeader(sipIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(properties.getId(), properties.getDomain());
        Address fromAddr = ADDRESS_FACTORY.createAddress(fromUri);
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(fromAddr, fromTag);
        SipURI toUri = ADDRESS_FACTORY.createSipURI(channelId, device.getHostAddress());
        Address toAddr = ADDRESS_FACTORY.createAddress(toUri);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(toAddr, null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.INVITE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.INVITE, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);
        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), sipIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        request.addHeader(contact);
        SubjectHeader subject = HEADER_FACTORY.createSubjectHeader(
                channelId + ":" + ssrc + "," + properties.getId() + ":0");
        request.addHeader(subject);
        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "SDP");
        request.setContent(sdpBody, ct);
        return request;
    }

    // ACK 沿用 200 OK 的 CallId/From/To，只把 CSeq 方法换成 ACK。
    public Request buildAckRequest(GbDevice device, SIPResponse response, String remoteHost, int remotePort)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();
        String sipIp = properties.resolveSipIp(localIp);

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
        ViaHeader via = HEADER_FACTORY.createViaHeader(sipIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqNumber, Request.ACK);

        Request ack = MESSAGE_FACTORY.createRequest(
                requestUri, Request.ACK, callId, cseq, from, to, viaHeaders, maxForwards);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), sipIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        ack.addHeader(contact);
        return ack;
    }

    public Request buildByeRequest(GbDevice device, GbInviteSession session)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();
        String sipIp = properties.resolveSipIp(localIp);

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(session.getChannelId(), device.getHostAddress());

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(sipIp, properties.getPort(), transport, SipUtils.newViaTag());
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

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), sipIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        request.addHeader(contact);
        return request;
    }

    // Dialog 内复用 callId/from-tag/to-tag，Via 换 branch，CSeq 继续递增。
    public Request buildInfoRequest(GbDevice device, GbInviteSession session, String rtspBody)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();
        String sipIp = properties.resolveSipIp(localIp);

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(session.getChannelId(), device.getHostAddress());

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(sipIp, properties.getPort(), transport, SipUtils.newViaTag());
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

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), sipIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        request.addHeader(contact);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "MANSRTSP");
        request.setContent(rtspBody, ct);
        return request;
    }

    // 首次订阅 tag 传 null，续期/退订复用原 dialog；expires=0 是退订。
    public Request buildSubscribeRequest(GbDevice device, String eventType, String xmlBody,
                                         String charset, int expires, CallIdHeader callId,
                                         String fromTag, String toTag)
            throws ParseException, InvalidArgumentException, java.io.UnsupportedEncodingException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();
        String sipIp = properties.resolveSipIp(localIp);
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(device.getDeviceId(), device.getHostAddress());
        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(sipIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(properties.getId(), properties.getDomain());
        Address fromAddr = ADDRESS_FACTORY.createAddress(fromUri);
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(fromAddr, fromTag);
        SipURI toUri = ADDRESS_FACTORY.createSipURI(device.getDeviceId(), device.getHostAddress());
        Address toAddr = ADDRESS_FACTORY.createAddress(toUri);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(toAddr, toTag);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.SUBSCRIBE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.SUBSCRIBE, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);
        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), sipIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        request.addHeader(contact);
        EventHeader eventHeader = HEADER_FACTORY.createEventHeader(eventType);
        request.addHeader(eventHeader);
        ExpiresHeader expiresHeader = HEADER_FACTORY.createExpiresHeader(Math.max(0, expires));
        request.addHeader(expiresHeader);

        // Body（MANSCDP+xml）——按 XML 声明的编码转字节，避免声明与实际编码不一致
        if (xmlBody != null && !xmlBody.isBlank()) {
            ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "MANSCDP+xml");
            byte[] bodyBytes = xmlBody.getBytes(charset);
            request.setContent(bodyBytes, ct);
        }
        return request;
    }

    // 广播方向和点播反过来：设备 recvonly，平台 sendonly。
    public Response buildBroadcastInviteOk(GbDevice device, SIPRequest request, String sdpBody, String localToTag)
            throws ParseException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String sipIp = properties.resolveSipIp(localIp);

        SIPResponse response = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.OK, request);
        response.getToHeader().setTag(localToTag);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), sipIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        response.addHeader(contact);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "SDP");
        response.setContent(sdpBody, ct);
        return response;
    }

    // 设备是主叫，所以 BYE 里 From 用本地 toTag，To 用设备 fromTag，别写反。
    public Request buildBroadcastByeRequest(GbDevice device, String channelId, String callId,
                                            String fromTag, String toTag)
            throws ParseException, InvalidArgumentException {
        String localIp = sipLayer.getLocalIp(device.getLocalIp());
        String transport = device.getTransport() == null ? "UDP" : device.getTransport();
        String sipIp = properties.resolveSipIp(localIp);

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(channelId, device.getHostAddress());

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(sipIp, properties.getPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        SipURI fromUri = ADDRESS_FACTORY.createSipURI(properties.getId(), properties.getDomain());
        Address fromAddr = ADDRESS_FACTORY.createAddress(fromUri);
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(fromAddr, toTag);

        SipURI toUri = ADDRESS_FACTORY.createSipURI(channelId, device.getHostAddress());
        Address toAddr = ADDRESS_FACTORY.createAddress(toUri);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(toAddr, fromTag);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CallIdHeader callIdHeader = HEADER_FACTORY.createCallIdHeader(callId);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.BYE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.BYE, callIdHeader, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(properties.getId(), sipIp + ":" + properties.getPort());
        Address contactAddr = ADDRESS_FACTORY.createAddress(contactUri);
        ContactHeader contact = HEADER_FACTORY.createContactHeader(contactAddr);
        request.addHeader(contact);
        return request;
    }

    public Response buildResponse(int statusCode, Request request) throws ParseException {
        return MESSAGE_FACTORY.createResponse(statusCode, request);
    }
}
