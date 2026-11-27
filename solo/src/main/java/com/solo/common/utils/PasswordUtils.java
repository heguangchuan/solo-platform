package com.solo.common.utils;

import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

public final class PasswordUtils {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 32;
    private static final int BCRYPT_STRENGTH = 12;
    private static final Pattern LEGACY_MD5 = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    private PasswordUtils() {
    }

    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new ServiceException("密码不能为空");
        }
        return ENCODER.encode(rawPassword);
    }

    /** 只给历史 MD5 数据留登录迁移通道，新密码一律 BCrypt。 */
    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isLegacyMd5(storedPassword)) {
            byte[] actual = md5(rawPassword).getBytes(StandardCharsets.US_ASCII);
            byte[] expected = storedPassword.toLowerCase().getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(actual, expected);
        }
        if (!BCRYPT.matcher(storedPassword).matches()) {
            return false;
        }
        try {
            return ENCODER.matches(rawPassword, storedPassword);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean isLegacyMd5(String storedPassword) {
        return storedPassword != null && LEGACY_MD5.matcher(storedPassword).matches();
    }

    /** 只识别历史 MD5；默认密码登录成功并转成 BCrypt 前会把重置标记持久化。 */
    public static boolean isDefaultPassword(String username, String hashedPassword) {
        if (username == null || !isLegacyMd5(hashedPassword)) {
            return false;
        }
        String userDefault = md5(String.format(SystemConstants.DEFAULT_PASSWORD_TEMPLATE, username));
        if (MessageDigest.isEqual(userDefault.getBytes(StandardCharsets.US_ASCII),
                hashedPassword.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            return true;
        }
        return "admin".equals(username) && MessageDigest.isEqual(
                SystemConstants.DEFAULT_PASSWORD.getBytes(StandardCharsets.US_ASCII),
                hashedPassword.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static String md5(String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ServiceException("密码校验失败");
        }
    }

    /**
     * 强密码校验：长度 [8,32]，四类字符至少占三类，不允许空白。
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
