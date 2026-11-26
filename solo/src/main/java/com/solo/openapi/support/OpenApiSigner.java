package com.solo.openapi.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

// 签名串固定六行：METHOD / PATH / 排序编码后的 QUERY / BODY_SHA256 / TIMESTAMP / NONCE。
public final class OpenApiSigner {

    private OpenApiSigner() {
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static String canonicalQuery(Map<String, String[]> parameterMap) {
        if (parameterMap == null || parameterMap.isEmpty()) {
            return "";
        }
        TreeMap<String, String[]> sorted = new TreeMap<>(parameterMap);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String[]> entry : sorted.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (!sb.isEmpty()) {
                    sb.append('&');
                }
                sb.append(entry.getKey()).append('=').append(urlEncode(value));
            }
        }
        return sb.toString();
    }

    public static String buildSignString(String method, String path, String canonicalQuery,
                                         String bodyHash, String timestamp, String nonce) {
        return String.join("\n",
                method == null ? "" : method.toUpperCase(),
                path == null ? "" : path,
                canonicalQuery == null ? "" : canonicalQuery,
                bodyHash == null ? "" : bodyHash,
                timestamp == null ? "" : timestamp,
                nonce == null ? "" : nonce);
    }

    public static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    // 签名别用 equals，走常量时间比对。
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
