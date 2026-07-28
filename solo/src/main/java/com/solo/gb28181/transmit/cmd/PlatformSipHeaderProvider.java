package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.utils.SipUtils;
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
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 级联出站 SIP 头构造器（本平台作为下级向上级平台发起请求）
 *
 * <p>与设备侧 {@link SipRequestHeaderProvider} 的区别：身份取自 {@link GbParentPlatform} 逐行配置
 * （{@code deviceGbId}/{@code deviceIp}/{@code devicePort}/{@code username}/{@code password}），
 * 而非全局 {@code gb28181.id}。故独立成类，见 ADR-018。</p>
 *
 * <ul>
 *   <li>Request-URI / To：{@code sip:<serverGbId>@<serverIp>:<serverPort>}（注册时 To=自己=serverGbId）</li>
 *   <li>From：{@code sip:<deviceGbId>@<serverGbDomain>}</li>
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

    /** 本平台对该上级使用的出口 IP（留空回落全局配置） */
    public String localIp(GbParentPlatform p) {
        return StringUtils.hasText(p.getDeviceIp()) ? p.getDeviceIp() : properties.getIp();
    }

    /** 本平台对该上级使用的出口端口（留空回落全局配置） */
    public int localPort(GbParentPlatform p) {
        return p.getDevicePort() != null && p.getDevicePort() > 0 ? p.getDevicePort() : properties.getPort();
    }

    /** 认证用户名（默认同 deviceGbId） */
    private String username(GbParentPlatform p) {
        return StringUtils.hasText(p.getUsername()) ? p.getUsername() : p.getDeviceGbId();
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
        String localIp = localIp(p);
        String transport = transport(p);
        String serverHost = p.getServerIp() + ":" + p.getServerPort();

        // Request-URI: sip:<serverGbId>@<serverHost>
        SipURI requestUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);

        // Via
        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(p), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        // From: sip:<deviceGbId>@<serverGbDomain>;tag=<fromTag>
        SipURI fromUri = ADDRESS_FACTORY.createSipURI(p.getDeviceGbId(), p.getServerGbDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), fromTag);

        // To: 注册语义 To=自己（deviceGbId@serverGbDomain），无 tag
        SipURI toUri = ADDRESS_FACTORY.createSipURI(p.getDeviceGbId(), p.getServerGbDomain());
        ToHeader toHeader = HEADER_FACTORY.createToHeader(ADDRESS_FACTORY.createAddress(toUri), null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.REGISTER);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.REGISTER, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        // Contact: sip:<deviceGbId>@<localIp>:<localPort>
        SipURI contactUri = ADDRESS_FACTORY.createSipURI(p.getDeviceGbId(), localIp + ":" + localPort(p));
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
        String localIp = localIp(p);
        String transport = transport(p);
        String serverHost = p.getServerIp() + ":" + p.getServerPort();

        SipURI requestUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);

        List<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader via = HEADER_FACTORY.createViaHeader(localIp, localPort(p), transport, SipUtils.newViaTag());
        via.setRPort();
        viaHeaders.add(via);

        SipURI fromUri = ADDRESS_FACTORY.createSipURI(p.getDeviceGbId(), p.getServerGbDomain());
        FromHeader fromHeader = HEADER_FACTORY.createFromHeader(ADDRESS_FACTORY.createAddress(fromUri), SipUtils.newFromTag());

        SipURI toUri = ADDRESS_FACTORY.createSipURI(p.getServerGbId(), serverHost);
        ToHeader toHeader = HEADER_FACTORY.createToHeader(ADDRESS_FACTORY.createAddress(toUri), null);

        MaxForwardsHeader maxForwards = HEADER_FACTORY.createMaxForwardsHeader(70);
        CSeqHeader cseq = HEADER_FACTORY.createCSeqHeader(cseqSeed.incrementAndGet(), Request.MESSAGE);

        Request request = MESSAGE_FACTORY.createRequest(
                requestUri, Request.MESSAGE, callId, cseq, fromHeader, toHeader, viaHeaders, maxForwards);

        ContentTypeHeader ct = HEADER_FACTORY.createContentTypeHeader("Application", "MANSCDP+xml");
        request.setContent(xmlBody, ct);
        return request;
    }

    private static String md5(String s) {
        return DigestUtils.md5DigestAsHex(s.getBytes(StandardCharsets.UTF_8));
    }
}
