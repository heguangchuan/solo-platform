package com.solo.gb28181.utils;

import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// 只给级联共享时撞码的通道重编号，别拿去生成设备自己的真实编码。
// 前 10 位用平台 domain，接着固定 131，最后 7 位由 deviceId + "|" + channelId 的 SHA-256 算出来。
// 同一个通道反复算结果不变；真撞码就让 gb_platform_share 的唯一键报错，别偷偷换个号。
@Component
@RequiredArgsConstructor
public class GbCodeGenerator {

    private static final String CHANNEL_TYPE = "131";

    private static final long SEQ_MOD = 10_000_000L;

    private final Gb28181Properties properties;

    public String nextExposedChannelCode(String deviceId, String channelId) {
        String prefix13 = domainPrefix10() + CHANNEL_TYPE;
        String input = safe(deviceId) + "|" + safe(channelId);
        long hash = hash64(input);
        // Math.abs(Long.MIN_VALUE) 还是负数，这里用 floorMod 才能一直拿到非负序号
        long seq = Math.floorMod(hash, SEQ_MOD);
        return prefix13 + String.format("%07d", seq);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private String domainPrefix10() {
        String domain = properties.getDomain();
        if (domain == null || !domain.matches("\\d{10}")) {
            throw new ServiceException("gb28181.domain 未配置或不是 10 位数字，无法生成国标编码");
        }
        return domain;
    }

    private static long hash64(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest, 0, 8).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
