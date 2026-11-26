package com.solo.gb28181.support;

import com.solo.config.JwtProperties;
import com.solo.common.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 录像文件限时访问链接签名：只绑定 ZLM 节点和文件相对路径，不暴露节点地址。 */
@Component
@RequiredArgsConstructor
public class RecordFileLinkSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final JwtProperties jwtProperties;

    public String sign(String serverId, String url, int expirySeconds) {
        if (serverId == null || serverId.isBlank() || url == null || url.isBlank()) {
            throw new ServiceException("录像文件位置缺失");
        }
        long expireAt = System.currentTimeMillis() / 1000 + expirySeconds;
        String payload = serverId + "\n" + url + "\n" + expireAt;
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + hmac(body);
    }

    public Link verify(String token) {
        if (token == null || token.isBlank()) {
            throw new ServiceException("录像访问链接无效");
        }
        int split = token.lastIndexOf('.');
        if (split <= 0 || split == token.length() - 1) {
            throw new ServiceException("录像访问链接格式错误");
        }
        String body = token.substring(0, split);
        String signature = token.substring(split + 1);
        if (!constantTimeEquals(hmac(body), signature)) {
            throw new ServiceException("录像访问链接签名错误");
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
            String[] parts = payload.split("\n", -1);
            if (parts.length != 3) {
                throw new ServiceException("录像访问链接内容错误");
            }
            long expireAt = Long.parseLong(parts[2]);
            if (System.currentTimeMillis() / 1000 > expireAt) {
                throw new ServiceException("录像访问链接已过期");
            }
            return new Link(parts[0], parts[1]);
        } catch (NumberFormatException e) {
            throw new ServiceException("录像访问链接内容错误");
        }
    }

    private String hmac(String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] bytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("生成录像访问签名失败", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public record Link(String serverId, String url) {
    }
}
