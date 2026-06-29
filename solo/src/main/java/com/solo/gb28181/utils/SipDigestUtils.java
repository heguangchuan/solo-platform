package com.solo.gb28181.utils;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SIP Digest 鉴权工具（RFC 2617 MD5）
 * <p>response = MD5( HA1 : nonce : HA2 )</p>
 * <p>HA1 = MD5( username : realm : password )，HA2 = MD5( method : uri )</p>
 */
@UtilityClass
public class SipDigestUtils {

    /**
     * 计算 RFC 2617 MD5 Digest response
     */
    public static String md5Digest(String username, String realm, String password,
                                   String method, String uri, String nonce) {
        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5(method + ":" + uri);
        return md5(ha1 + ":" + nonce + ":" + ha2);
    }

    /**
     * 生成 nonce（轻量级，足够用于设备一次性鉴权）
     */
    public static String genNonce() {
        return md5(Long.toHexString(System.nanoTime()) + ":" + Long.toHexString(System.currentTimeMillis()));
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
