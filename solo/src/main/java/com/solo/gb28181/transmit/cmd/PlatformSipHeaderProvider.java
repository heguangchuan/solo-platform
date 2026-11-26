package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import javax.sip.InvalidArgumentException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.AuthorizationHeader;
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
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
@Component
// 本平台的编号、域和出口都从全局配置取；上级配置里只放对端信息。
public class PlatformSipHeaderProvider {

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

    private final AtomicLong cseqSeed = new AtomicLong(1L);

    public PlatformSipHeaderProvider(Gb28181Properties properties, @Lazy SipLayer sipLayer) {
        this.properties = properties;
        this.sipLayer = sipLayer;
    }

    public String localIp() {
        return properties.getIp();
    }

    public int localPort() {
        return properties.getPort();
    }

    public String deviceDomain() {
        return properties.getDomain();
    }

    public String deviceGbId() {
        return properties.getId();
    }

    private String username(GbParentPlatform p) {
        return StringUtils.hasText(p.getUsername()) ? p.getUsername() : deviceGbId();
    }

    private String transport(GbParentPlatform p) {
        return StringUtils.hasText(p.getTransport()) ? p.getTransport() : "UDP";
    }

    // 401 后重发要复用 callId/fromTag；expires=0 是注销。
    public Request createRegisterRequest(GbParentPlatform p, CallIdHeader callId, String fromTag,
                                         int expires, WWWAuthenticateHeader www)
            throws ParseException, InvalidArgumentException {
        String localIp = localIp();
        String transport = transport(p);
        String serverHost = p.getServerIp() + ":" + p.getServerPort();
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);
        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), deviceDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), fromTag);
        SipURI toUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), p.getServerGbDomain());
        ToHeader toHeader = HEADER_FACTORY.createToHeader(ADDRESS_FACTORY.createAddress(toUri), null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.REGISTER);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.REGISTER, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);
        SipURI contactUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), localIp + ":" + localPort());
        ContactHeader contact = HEADER_FACTORY.createContactHeader(ADDRESS_FACTORY.createAddress(contactUri));
        request.addHeader(contact);

        ExpiresHeader expiresHeader = HEADER_FACTORY.createExpiresHeader(expires);
        request.addHeader(expiresHeader);

        if (www != null) {
            request.addHeader(buildAuthorization(p, requestUri, www));
        }
        return request;
    }

    // 有 qop 和没 qop 是两套 Digest 公式，老设备经常不带 qop。
    private AuthorizationHeader buildAuthorization(GbParentPlatform p, SipURI requestUri, WWWAuthenticateHeader www)
            throws ParseException {
        String realm = www.getRealm();
        String nonce = www.getNonce();
        String scheme = www.getScheme();
        String qop = www.getQop();
        String user = username(p);

        String ha1 = md5(user + ":" + realm + ":" + p.getPassword());
        String ha2 = md5(Request.REGISTER + ":" + requestUri.toString());

        String nc = "00000001";
        String cnonce = UUID.randomUUID().toString().replace("-", "");
        String response;
        if (StringUtils.hasText(qop)) {
            response = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
        } else {
            response = md5(ha1 + ":" + nonce + ":" + ha2);
        }

        AuthorizationHeader auth = HEADER_FACTORY.createAuthorizationHeader(scheme);
        auth.setUsername(user);
        auth.setRealm(realm);
        auth.setNonce(nonce);
        auth.setURI(requestUri);
        auth.setResponse(response);
        auth.setAlgorithm("MD5");
        if (StringUtils.hasText(qop)) {
            auth.setQop(qop);
            auth.setCNonce(cnonce);
            auth.setNonceCount(1);
        }
        return auth;
    }

    public Request createMessageRequest(GbParentPlatform p, String xmlBody, CallIdHeader callId)
            throws ParseException, InvalidArgumentException {
        String localIp = localIp();
        String transport = transport(p);
        String serverHost = p.getServerIp() + ":" + p.getServerPort();

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        SipURI fromUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), deviceDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), SipUtils.newFromTag());

        SipURI toUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(ADDRESS_FACTORY.createAddress(toUri), null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.MESSAGE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.MESSAGE, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "MANSCDP+xml");
        // 按平台声明字符集编码消息体：xmlBody 的 XML 声明写的是 platform.characterSet（默认 GB2312），
        // 若交给 JAIN-SIP 以 String 落字节会默认按 UTF-8 编码，与声明不符 → 上级按 GB2312 解出乱码。
        // 显式转成对应字符集的 byte[]，setContent(byte[]) 原样落字节，保证声明与字节一致（见 ADR-009 编码链路）。
        Charset charset = resolveCharset(p.getCharacterSet());
        request.setContent(xmlBody.getBytes(charset), ct);
        return request;
    }

    private static String md5(String s) {
        return DigestUtils.md5DigestAsHex(s.getBytes(StandardCharsets.UTF_8));
    }

    private static Charset resolveCharset(String name) {
        String cs = (name == null || name.isBlank()) ? "GB2312" : name.trim();
        try {
            return Charset.forName(cs);
        } catch (Exception e) {
            try {
                return Charset.forName("GBK");
            } catch (Exception ex) {
                return StandardCharsets.UTF_8;
            }
        }
    }

    // 这次 INVITE 是上级发的：From 用本平台 toTag，To 用上级 fromTag。
    public Request createByeToUpstream(GbParentPlatform p, String callId,
                                       String upstreamFromTag, String localToTag)
            throws ParseException, InvalidArgumentException {
        String localIp = localIp();
        String transport = transport(p);
        String serverHost = p.getServerIp() + ":" + p.getServerPort();

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), deviceDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), localToTag);
        SipURI toUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(ADDRESS_FACTORY.createAddress(toUri), upstreamFromTag);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CallIdHeader callIdHeader = HEADER_FACTORY.createCallIdHeader(callId);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.BYE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.BYE, callIdHeader, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), localIp + ":" + localPort());
        ContactHeader contact = HEADER_FACTORY.createContactHeader(ADDRESS_FACTORY.createAddress(contactUri));
        request.addHeader(contact);
        return request;
    }

    // localToTag 还要存进会话，后面 BYE 得接着用。
    public SIPResponse buildInviteOk(GbParentPlatform p, SIPRequest request, String sdpBody, String localToTag)
            throws ParseException {
        SIPResponse response = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.OK, request);
        response.getToHeader().setTag(localToTag);

        String localIp = localIp();
        SipURI contactUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), localIp + ":" + localPort());
        ContactHeader contact = HEADER_FACTORY.createContactHeader(ADDRESS_FACTORY.createAddress(contactUri));
        response.setHeader(contact);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "SDP");
        response.setContent(sdpBody, ct);
        return response;
    }

    public SIPResponse buildResponse(SIPRequest request, int statusCode, String reason) throws ParseException {
        if (request.getToHeader().getTag() == null && statusCode >= 200) {
            request.getToHeader().setTag(SipUtils.newToTag());
        }
        SIPResponse response = (SIPResponse) MESSAGE_FACTORY.createResponse(statusCode, request);
        if (reason != null) {
            response.setReasonPhrase(reason);
        }
        return response;
    }

    // NOTIFY 必须带 Event 和 Subscription-State: active。
    public Request createNotifyRequest(GbParentPlatform p, String eventType, String xmlBody, CallIdHeader callId)
            throws ParseException, InvalidArgumentException {
        String localIp = localIp();
        String transport = transport(p);
        String serverHost = p.getServerIp() + ":" + p.getServerPort();

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        SipURI fromUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), deviceDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), SipUtils.newFromTag());

        SipURI toUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(ADDRESS_FACTORY.createAddress(toUri), null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.NOTIFY);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.NOTIFY, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);
        EventHeader eventHeader = HEADER_FACTORY.createEventHeader(eventType);
        request.addHeader(eventHeader);
        request.addHeader(HEADER_FACTORY.createHeader("Subscription-State", "active"));
        SipURI contactUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), localIp + ":" + localPort());
        ContactHeader contact = HEADER_FACTORY.createContactHeader(ADDRESS_FACTORY.createAddress(contactUri));
        request.addHeader(contact);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "MANSCDP+xml");
        // 按平台声明字符集编码（与 createMessageRequest 同链路，见 ADR-009）
        java.nio.charset.Charset charset = resolveCharset(p.getCharacterSet());
        request.setContent(xmlBody.getBytes(charset), ct);
        return request;
    }

    public CallIdHeader newCallId(GbParentPlatform p) {
        String localIp = localIp();
        String transport = transport(p);
        SipProviderImpl provider = "TCP".equalsIgnoreCase(transport)
                ? sipLayer.getTcpSipProvider(localIp)
                : sipLayer.getUdpSipProvider(localIp);
        if (provider == null) provider = sipLayer.getUdpSipProvider();
        return provider == null ? null : provider.getNewCallId();
    }

    // 反向 INVITE 由本平台做主叫，To 不带 tag。
    public Request createInviteToUpstream(GbParentPlatform p, String channelId, String ssrc,
                                          String sdpBody, CallIdHeader callId, String fromTag)
            throws ParseException, InvalidArgumentException {
        String localIp = localIp();
        String transport = transport(p);
        String serverHost = p.getServerIp() + ":" + p.getServerPort();

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        SipURI fromUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), deviceDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), fromTag);

        SipURI toUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(ADDRESS_FACTORY.createAddress(toUri), null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.INVITE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.INVITE, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), localIp + ":" + localPort());
        request.addHeader(HEADER_FACTORY.createContactHeader(ADDRESS_FACTORY.createAddress(contactUri)));

        SubjectHeader subject = HEADER_FACTORY.createSubjectHeader(
                channelId + ":" + ssrc + "," + deviceGbId() + ":0");
        request.addHeader(subject);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "SDP");
        request.setContent(sdpBody, ct);
        return request;
    }

    // Request-URI 先取 SDP o= 的用户名，取不到再退到 To。
    public Request buildAckToUpstream(GbParentPlatform p, SIPResponse response, String remoteHost, int remotePort)
            throws ParseException, InvalidArgumentException {
        String localIp = localIp();
        String transport = transport(p);

        FromHeader from = (FromHeader) response.getHeader(FromHeader.NAME);
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        CallIdHeader callId = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        CSeqHeader respCseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        long cseqNumber = respCseq == null ? cseqSeed.incrementAndGet() : respCseq.getSeqNumber();

        byte[] raw = response.getRawContent();
        String username = SdpBuilder.extractOriginUser(raw == null ? null : new String(raw));
        if (username == null) {
            javax.sip.address.URI toUri = to.getAddress().getURI();
            if (toUri instanceof SipURI sip) {
                username = sip.getUser();
            }
        }
        if (username == null || username.isBlank()) {
            username = p.getServerGbId();
        }
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(username, remoteHost + ":" + remotePort);

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqNumber, Request.ACK);

        Request ack = MESSAGE_FACTORY.createRequest(
                requestUri, Request.ACK, callId, cseq, from, to, viaHeaders, maxForwards);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), localIp + ":" + localPort());
        ack.addHeader(HEADER_FACTORY.createContactHeader(ADDRESS_FACTORY.createAddress(contactUri)));
        return ack;
    }

    // 本平台是主叫，tag 方向和 createByeToUpstream 反过来。
    public Request createByeAsCaller(GbParentPlatform p, String callId,
                                     String localFromTag, String upstreamToTag)
            throws ParseException, InvalidArgumentException {
        String localIp = localIp();
        String transport = transport(p);
        String serverHost = p.getServerIp() + ":" + p.getServerPort();

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        SipURI fromUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), deviceDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), localFromTag);

        SipURI toUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(ADDRESS_FACTORY.createAddress(toUri), upstreamToTag);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CallIdHeader callIdHeader = HEADER_FACTORY.createCallIdHeader(callId);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.BYE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.BYE, callIdHeader, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        SipURI contactUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), localIp + ":" + localPort());
        request.addHeader(HEADER_FACTORY.createContactHeader(ADDRESS_FACTORY.createAddress(contactUri)));
        return request;
    }

    public Request buildBroadcastResponse(GbParentPlatform p, int sn, String channelId, boolean ok)
            throws ParseException, InvalidArgumentException {
        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\r\n"
                + "<Response>\r\n"
                + "<CmdType>Broadcast</CmdType>\r\n"
                + "<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + channelId + "</DeviceID>\r\n"
                + "<Result>" + (ok ? "OK" : "ERROR") + "</Result>\r\n"
                + "</Response>";
        CallIdHeader callId = HEADER_FACTORY.createCallIdHeader(UUID.randomUUID().toString() + "@" + localIp());
        return createMessageRequest(p, xml, callId);
    }
}
