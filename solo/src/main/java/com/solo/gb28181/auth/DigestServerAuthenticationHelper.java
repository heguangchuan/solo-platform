package com.solo.gb28181.auth;

import lombok.extern.slf4j.Slf4j;

import javax.sip.address.URI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * <p>完整实现包含 qop=auth + cnonce + nc 的版本，符合国标设备实际行为：
 * 海康/大华/宇视/华为等设备 401 挑战收到 qop=auth 后，都会按完整 KD 算 response。</p>
 *
 * <p>
 *   HA1 = MD5( username : realm : password )<br/>
 *   HA2 = MD5( method : uri )<br/>
 *   qop=auth :  response = MD5( HA1 : nonce : nc : cnonce : qop : HA2 )<br/>
 *   无 qop :     response = MD5( HA1 : nonce : HA2 )
 * </p>
 */
@Slf4j
public class DigestServerAuthenticationHelper {

    private static final String ALGO = "MD5";
    private static final String SCHEME = "Digest";

    private static final char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MessageDigest md;

    public DigestServerAuthenticationHelper() throws NoSuchAlgorithmException {
        this.md = MessageDigest.getInstance(ALGO);
    }

    /**
     * 生成 401 挑战头并塞到响应里
     */
    public void appendChallenge(HeaderFactory headerFactory, Response response, String realm) {
        try {
            WWWAuthenticateHeader auth = headerFactory.createWWWAuthenticateHeader(SCHEME);
            auth.setParameter("realm", realm);
            auth.setParameter("qop", "auth");
            auth.setParameter("nonce", generateNonce());
            auth.setParameter("algorithm", ALGO);
            response.setHeader(auth);
        } catch (Exception ex) {
            log.error("[设备鉴权] 构造 401 Digest 挑战失败", ex);
        }
    }

    /**
     * 用明文口令校验设备 REGISTER 的 Authorization 头
     *
     * @return true=通过
     */
    public boolean authenticate(Request request, String password) {
        AuthorizationHeader auth = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
        if (auth == null || auth.getRealm() == null) return false;

        String realm = auth.getRealm().trim();
        String username = auth.getUsername() == null ? null : auth.getUsername().trim();
        if (username == null) return false;

        String nonce = auth.getNonce();
        URI uri = auth.getURI();
        if (uri == null || nonce == null) return false;

        String qop = auth.getQop();
        String cnonce = auth.getCNonce();
        int nc = auth.getNonceCount();
        String ncStr = String.format("%08x", nc).toUpperCase();

        String a1 = username + ":" + realm + ":" + password;
        String a2 = request.getMethod().toUpperCase() + ":" + uri.toString();
        String ha1 = md5Hex(a1);
        String ha2 = md5Hex(a2);

        StringBuilder kd = new StringBuilder(ha1).append(':').append(nonce);
        if (qop != null && qop.equalsIgnoreCase("auth")) {
            if (nc != -1) kd.append(':').append(ncStr);
            if (cnonce != null) kd.append(':').append(cnonce);
            kd.append(':').append(qop);
        }
        kd.append(':').append(ha2);

        String expected = md5Hex(kd.toString());
        String actual = auth.getResponse();

        if (log.isDebugEnabled()) {
            log.debug("[设备鉴权] Digest 校验: user={} realm={} qop={} nc={} cnonce={} expected={} actual={}",
                    username, realm, qop, ncStr, cnonce, expected, actual);
        }
        return expected.equalsIgnoreCase(actual);
    }

    private String generateNonce() {
        byte[] buf = new byte[16];
        RANDOM.nextBytes(buf);
        byte[] mdBytes = md.digest((System.nanoTime() + ":" + toHex(buf)).getBytes());
        return toHex(mdBytes);
    }

    private String md5Hex(String s) {
        return toHex(md.digest(s.getBytes()));
    }

    private static String toHex(byte[] b) {
        char[] c = new char[b.length * 2];
        int pos = 0;
        for (byte v : b) {
            c[pos++] = HEX[(v >> 4) & 0x0F];
            c[pos++] = HEX[v & 0x0F];
        }
        return new String(c);
    }
}
