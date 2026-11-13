package com.solo.gb28181.auth;

import com.solo.gb28181.constant.GbRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sip.address.URI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * REGISTER 服务端 Digest 鉴权。
 *
 * <p>兼容 qop=auth 与不带 qop 的传统设备。nonce 必须由平台生成并写入 Redis，
 * 校验时同时核对设备 ID、realm 和有效期，避免客户端自行编造 nonce。</p>
 *
 * <p>HA1 = MD5(username:realm:password)<br>
 * HA2 = MD5(method:uri)<br>
 * qop=auth：response = MD5(HA1:nonce:nc:cnonce:qop:HA2)<br>
 * 无 qop：response = MD5(HA1:nonce:HA2)</p>
 */
@Component
@RequiredArgsConstructor
public class DigestServerAuthenticationHelper {

    private static final String ALGORITHM = "MD5";
    private static final String SCHEME = "Digest";
    private static final String QOP_AUTH = "auth";
    private static final Duration NONCE_TTL = Duration.ofMinutes(5);
    private static final int NONCE_BYTES = 32;
    private static final int NONCE_LENGTH = NONCE_BYTES * 2;
    private static final int DIGEST_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成新的 nonce，保存其设备绑定关系，并向响应写入 401 挑战头。
     * Redis 写入失败时直接抛出异常，调用方不得继续接受注册。
     */
    public void appendChallenge(HeaderFactory headerFactory, Response response,
                                String deviceId, String realm) throws ParseException {
        String nonce = generateNonce();
        redisTemplate.opsForValue().set(
                GbRedisKeys.REGISTER_NONCE_PREFIX + nonce,
                nonceBinding(deviceId, realm),
                NONCE_TTL);

        WWWAuthenticateHeader auth = headerFactory.createWWWAuthenticateHeader(SCHEME);
        auth.setParameter("realm", realm);
        auth.setParameter("qop", QOP_AUTH);
        auth.setParameter("nonce", nonce);
        auth.setParameter("algorithm", ALGORITHM);
        response.setHeader(auth);
    }

    /**
     * 校验 REGISTER 的 Authorization 头。任何字段缺失、nonce 未签发或已过期、
     * 设备/realm/URI 不匹配、摘要错误都返回 false。
     */
    public boolean authenticate(Request request, String deviceId, String expectedRealm, String password) {
        AuthorizationHeader auth = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
        if (auth == null || deviceId == null || expectedRealm == null || password == null) {
            return false;
        }

        String username = auth.getUsername();
        String realm = auth.getRealm();
        String nonce = auth.getNonce();
        URI digestUri = auth.getURI();
        URI requestUri = request.getRequestURI();
        if (!deviceId.equals(username) || !expectedRealm.equals(realm)
                || !isHex(nonce, NONCE_LENGTH) || digestUri == null || requestUri == null
                || !requestUri.toString().equals(digestUri.toString())) {
            return false;
        }

        String algorithm = auth.getAlgorithm();
        if (algorithm != null && !algorithm.isBlank() && !ALGORITHM.equalsIgnoreCase(algorithm)) {
            return false;
        }

        String issuedBinding = redisTemplate.opsForValue().get(GbRedisKeys.REGISTER_NONCE_PREFIX + nonce);
        if (!nonceBinding(deviceId, expectedRealm).equals(issuedBinding)) {
            return false;
        }

        String ha1 = md5Hex(username + ":" + realm + ":" + password);
        String ha2 = md5Hex(request.getMethod().toUpperCase(Locale.ROOT) + ":" + digestUri);
        StringBuilder kd = new StringBuilder(ha1).append(':').append(nonce);
        String qop = auth.getQop();
        if (qop != null && !qop.isBlank()) {
            if (!QOP_AUTH.equalsIgnoreCase(qop)) {
                return false;
            }
            String nc = auth.getParameter("nc");
            String cnonce = auth.getCNonce();
            if (!isNonceCount(nc) || cnonce == null || cnonce.isBlank()) {
                return false;
            }
            // 必须使用设备报文中的原始 nc，不能用整数重新格式化，否则大小写变化会导致摘要不同。
            kd.append(':').append(nc)
                    .append(':').append(cnonce)
                    .append(':').append(qop);
        }
        kd.append(':').append(ha2);

        String expected = md5Hex(kd.toString());
        String actual = auth.getResponse();
        if (!isHex(actual, DIGEST_LENGTH)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private static String nonceBinding(String deviceId, String realm) {
        return deviceId + "\n" + realm;
    }

    private static String generateNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static boolean isNonceCount(String nc) {
        return nc != null && nc.length() == 8 && nc.chars().allMatch(DigestServerAuthenticationHelper::isHexDigit);
    }

    private static boolean isHex(String value, int expectedLength) {
        return value != null && value.length() == expectedLength
                && value.chars().allMatch(DigestServerAuthenticationHelper::isHexDigit);
    }

    private static boolean isHexDigit(int c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    private static String md5Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 MD5", e);
        }
    }
}
