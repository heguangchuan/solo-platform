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

/**
 * <p>与设备侧 {@link SipRequestHeaderProvider} 的区别：身份信息（本平台国标编号 / 域 / 出口 IP / 端口）一律
 * 取自全局 {@link Gb28181Properties}，per-upper 只存对端配置（{@code serverGbId} / {@code serverIp} / {@code serverPort} /
 * {@code username} / {@code password}）。故独立成类，见 ADR-018 + ADR-036。</p>
 *
 * <ul>
 *   <li>Request-URI / To：{@code sip:<serverGbId>@<serverIp>:<serverPort>}（注册时 To=自己=serverGbId）</li>
 *   <li>From：{@code sip:<deviceGbId>@<deviceDomain>}（本平台国标编号 + 本平台域，ADR-035）</li>
 *   <li>Contact：{@code sip:<deviceGbId>@<localIp>:<localPort>}</li>
 * </ul>
 */
@Component
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

    /** 全局 CSeq 序列（与设备侧独立即可，SIP 不要求跨会话连续） */
    private final AtomicLong cseqSeed = new AtomicLong(1L);

    public PlatformSipHeaderProvider(Gb28181Properties properties, @Lazy SipLayer sipLayer) {
        this.properties = properties;
        this.sipLayer = sipLayer;
    }

    /**
     * 本平台 SIP 出口 IP（全局，ADR-036）
     */
    public String localIp() {
        return properties.getIp();
    }

    /**
     * 本平台 SIP 出口端口（全局，ADR-036）
     */
    public int localPort() {
        return properties.getPort();
    }

    /**
     * 本平台国标域（From / Contact 头用，全局，ADR-036）
     */
    public String deviceDomain() {
        return properties.getDomain();
    }

    /**
     * 本平台国标编号（向上注册时 From / Contact / 200 OK 头用，全局，ADR-036）
     */
    public String deviceGbId() {
        return properties.getId();
    }

    /** 认证用户名（默认同 deviceGbId()） */
    private String username(GbParentPlatform p) {
        return StringUtils.hasText(p.getUsername()) ? p.getUsername() : deviceGbId();
    }

    private String transport(GbParentPlatform p) {
        return StringUtils.hasText(p.getTransport()) ? p.getTransport() : "UDP";
    }

    /**
     * 构造 REGISTER 请求（首次无 Authorization，或带 Digest 应答）。
     *
     * @param p        上级平台配置
     * @param callId   Call-Id（首次新建；401 重发时复用响应回显的同一个）
     * @param fromTag  from-tag（首次新建；401 重发时复用）
     * @param expires  注册有效期秒（注销时传 0）
     * @param www      401 挑战头；首次注册传 null
     */
    public Request createRegisterRequest(GbParentPlatform p, CallIdHeader callId, String fromTag,
                                         int expires, WWWAuthenticateHeader www)
            throws ParseException, InvalidArgumentException {
        String localIp = localIp();
        String transport = transport(p);
        String serverHost = p.getServerIp() + ":" + p.getServerPort();

        // Request-URI: sip:<serverGbId>@<serverHost>
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);

        // Via
        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        // From: sip:<deviceGbId>@<deviceDomain>（本平台国标域，ADR-035）；tag=<fromTag>
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), deviceDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), fromTag);

        // To: 注册语义 To=自己（deviceGbId@serverGbDomain，registrar 域），无 tag
        SipURI toUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), p.getServerGbDomain());
        ToHeader toHeader = HEADER_FACTORY.createToHeader(ADDRESS_FACTORY.createAddress(toUri), null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.REGISTER);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.REGISTER, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        // Contact: sip:<deviceGbId>@<localIp>:<localPort>
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

    /**
     * 客户端 Digest 应答（RFC 2617，qop=auth 分支）。
     * <p>HA1 = md5(username:realm:password)，HA2 = md5(REGISTER:requestUri)，
     * response = qop 存在时 md5(HA1:nonce:nc:cnonce:qop:HA2)，否则 md5(HA1:nonce:HA2)。</p>
     */
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

    /**
     * 构造发往上级的 MESSAGE（Keepalive Notify 等）。
     */
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

    /**
     * 解析平台配置的字符集声明，与 XML 声明保持一致；默认 GB2312。
     * GB2312 编码不识别时回退到 GBK（GB2312 的超集，兼容更多中文），再不行回退 UTF-8。
     */
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

    /**
     * 构造对上级的 BYE（本平台作为被叫 UAS 主动结束推流会话：无 ACK 超时 / 无人观看兜底）。
     * <p>Dialog 角色与注册相反：本次 INVITE 由上级发起，本平台是被叫。故 From=本平台(deviceGbId, toTag)、
     * To=上级(serverGbId, fromTag)、Request-URI 指向上级。复用会话记录的 callId / fromTag(上级) / toTag(本平台)。</p>
     *
     * @param p        上级平台配置
     * @param callId   上级 INVITE 的 Call-Id
     * @param upstreamFromTag 上级 INVITE 的 from-tag（对本平台而言是 To-tag 方向）
     * @param localToTag      本平台回 200 OK 时生成的 to-tag
     */
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

        // From = 本平台（被叫方，带本平台在 200 OK 里生成的 to-tag；host 用本平台国标域，ADR-035）
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), deviceDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), localToTag);

        // To = 上级（主叫方，带上级 INVITE 的 from-tag）
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

    /**
     * 构造对上级 INVITE 的 200 OK（携带 sendonly SDP + Contact + To-tag）。
     * <p>本平台作为被叫应答上级点播。To-tag 由本方法写入 {@code request} 回显出的 To 头，
     * 调用方须把它持久化到会话（后续 BYE 要用）。Contact 指向本平台 SIP 地址，供上级发 ACK/BYE 定位。</p>
     *
     * @param p          上级平台配置
     * @param request    上级发来的 INVITE 原始请求（回显 Via/From/To/Call-Id/CSeq）
     * @param sdpBody    本平台 sendonly SDP（{@link SdpBuilder#buildSendAnswer}）
     * @param localToTag 本平台生成的 to-tag（须与写入会话的一致）
     */
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

    /**
     * 构造无 body 的简单状态应答（100 Trying / 404 / 400 / 486 / 500 等），并按需补 To-tag。
     * <p>供级联被点播编排在异步取流失败等场景回错误码用。</p>
     */
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

    /**
     * 构造发往上级的 NOTIFY 请求（ADR-030 订阅推送）。
     * <p>NOTIFY 必须带 {@code Event} 头（RFC 6665）+ {@code Subscription-State} 头标识当前订阅仍 active；
     * body 是 MANSCDP+xml，与发往设备的 MESSAGE body 同套字符集规则。</p>
     *
     * @param p         上级平台配置
     * @param eventType 事件类型小写驼峰：catalog / mobilePosition / alarm
     * @param xmlBody   MANSCDP+xml 报文体（{@link PlatformNotifyXml} 构造）
     * @param callId    调用方持有的 Call-Id
     */
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

        // Event 头：必填，事件类型（catalog / mobilePosition / alarm）
        EventHeader eventHeader = HEADER_FACTORY.createEventHeader(eventType);
        request.addHeader(eventHeader);
        // Subscription-State：active 表示订阅仍有效；reason 无需填
        request.addHeader(HEADER_FACTORY.createHeader("Subscription-State", "active"));

        // Contact：与 REGISTER / MESSAGE 一致
        SipURI contactUri = ADDRESS_FACTORY.createSipURI(deviceGbId(), localIp + ":" + localPort());
        ContactHeader contact = HEADER_FACTORY.createContactHeader(ADDRESS_FACTORY.createAddress(contactUri));
        request.addHeader(contact);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "MANSCDP+xml");
        // 按平台声明字符集编码（与 createMessageRequest 同链路，见 ADR-009）
        java.nio.charset.Charset charset = resolveCharset(p.getCharacterSet());
        request.setContent(xmlBody.getBytes(charset), ct);
        return request;
    }

    /**
     * 用上级平台配置的传输协议取一个新 Call-Id。
     */
    public CallIdHeader newCallId(GbParentPlatform p) {
        String localIp = localIp();
        String transport = transport(p);
        SipProviderImpl provider = "TCP".equalsIgnoreCase(transport)
                ? sipLayer.getTcpSipProvider(localIp)
                : sipLayer.getUdpSipProvider(localIp);
        if (provider == null) provider = sipLayer.getUdpSipProvider();
        return provider == null ? null : provider.getNewCallId();
    }

    /**
     * 级联喊话反向 INVITE 上级（recvonly offer），本平台为主叫，To 不带 tag。
     */
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

    /**
     * 反向 INVITE 收到 200 OK 后的 ACK，Dialog 头沿用响应，Request-URI 取 o= user 回退 To user。
     */
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

    /**
     * 本平台作为主叫结束级联喊话 Dialog 的 BYE，标签方向与 createByeToUpstream 相反。
     */
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

    /**
     * 收到上级 Broadcast NOTIFY 后回的 MESSAGE，Result=OK/ERROR，SN 透传。
     */
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
