package com.solo.common.utils;

import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PasswordUtils {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 32;

    private PasswordUtils() {
    }

    public static String md5(String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ServiceException("密码加密失败");
        }
    }

    /**
     * 默认密码格式：{username}@123456
     */
    public static String defaultPasswordOf(String username) {
        return String.format(SystemConstants.DEFAULT_PASSWORD_TEMPLATE, username);
    }

    public static String defaultPasswordHashOf(String username) {
        return md5(defaultPasswordOf(username));
    }

    public static boolean isDefaultPassword(String username, String hashedPassword) {
        if (username == null || hashedPassword == null) return false;
        return defaultPasswordHashOf(username).equals(hashedPassword);
    }

    /**
     * 强密码校验：
     * - 长度 [8,32]；
     * - 至少包含大写字母、小写字母、数字、特殊字符 4 类中的 3 类；
     * - 不允许包含空白字符。
     * 校验不通过抛 {@link ServiceException}。
     */
    public static void requireStrong(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new ServiceException("密码不能为空");
        }
        int len = rawPassword.length();
        if (len < MIN_LENGTH || len > MAX_LENGTH) {
            throw new ServiceException("密码长度必须在 " + MIN_LENGTH + "-" + MAX_LENGTH + " 位之间");
        }
        boolean upper = false, lower = false, digit = false, special = false;
        for (int i = 0; i < len; i++) {
            char c = rawPassword.charAt(i);
            if (Character.isWhitespace(c)) {
                throw new ServiceException("密码不能包含空格");
            }
            if (c >= 'A' && c <= 'Z') upper = true;
            else if (c >= 'a' && c <= 'z') lower = true;
            else if (c >= '0' && c <= '9') digit = true;
            else special = true;
        }
        int kinds = (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (special ? 1 : 0);
        if (kinds < 3) {
            throw new ServiceException("密码必须包含大写字母、小写字母、数字、特殊字符中的至少 3 类");
        }
    }
}
