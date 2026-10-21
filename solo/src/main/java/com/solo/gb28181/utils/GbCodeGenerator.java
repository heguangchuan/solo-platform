package com.solo.gb28181.utils;

import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <p>20 位国标编码结构：{@code [10位 domain][3位 131][7位 hash 末位]}。前 10 位统一取本平台
 * {@link Gb28181Properties#getDomain()}（即本平台的行政区划中心编码），第 11-13 位固定通道类型码
 * 131（摄像机），末 7 位由 {@code SHA-256(deviceId + "|" + channelId)} 截前 8 字节作为 long
 * 后 mod 10⁷ 派生。</p>
 *
 * <p><b>稳定性</b>：同 {@code (deviceId, channelId)} 永远得到同一码——纯函数、无状态、无随机、
 * 无扫描。同一通道在多次 saveShared、跨上级共享、跨 JVM 部署下都稳定，上级缓存的旧
 * {@code DeviceID} 不失效（解决 ADR-020 决策 4 强调的"已分码不收回"语义）。</p>
 *
 * <p><b>唯一性</b>：{@code hash64} 截 7 位理论撞码率 ≈ 10⁻⁷；库层
 * {@code gb_platform_share.uk_platform_exposed_code} 兜底。日常业务不会触发 UK 违规，
 * 10⁻⁷ 量级的撞码由库层抛 {@code DuplicateKeyException} 业务可见，不静默重试。</p>
 *
 * <p><b>仅服务级联共享场景</b>。设备/通道的国标编码一律由运营在管理面手工填入，平台不再自动生成——
 * 自动生成会与设备侧真实编码规则冲突（GB/T 28181 语义上编码由物理设备自身分配），且给运营造成
 * 「系统已经算好了」的错觉。本生成器只用于"对上级共享时撞码集合"中需要重码的子集（ADR-026 决策 1）。</p>
 */
@Component
@RequiredArgsConstructor
public class GbCodeGenerator {

    /** 通道类型码：131 摄像机 */
    private static final String CHANNEL_TYPE = "131";

    /** 末 7 位序号容量 */
    private static final long SEQ_MOD = 10_000_000L;

    private final Gb28181Properties properties;

    /**
     * 按 {@code (deviceId, channelId)} 计算平台命名空间内唯一的 20 位「对上级暴露」通道国标码。
     *
     * <p>纯函数——可重复调用、可并发调用、可跨进程调用，结果一致。不读库、不写库、不持有状态。</p>
     *
     * <p>库层唯一性兜底：{@code gb_platform_share.uk_platform_exposed_code}。理论上 hash 撞码
     * 概率 ≈ 10⁻⁷（一次撞码集合内分配 10 个码 = 10⁻⁶；按当前业务撞码本身是事故级频率，10 年内
     * 几乎不触发），触发时由库层抛错，业务层不静默重试，由运营/监控介入。</p>
     *
     * @param deviceId  设备国标码（{@code GbDeviceChannel.deviceId}），{@code null} 视为空串
     * @param channelId 通道国标码（{@code GbDeviceChannel.channelId}），{@code null} 视为空串
     * @return 20 位纯数字 GB 28181 通道编码
     */
    public String nextExposedChannelCode(String deviceId, String channelId) {
        String prefix13 = domainPrefix10() + CHANNEL_TYPE;
        String input = safe(deviceId) + "|" + safe(channelId);
        long hash = hash64(input);
        // Math.floorMod 永远返回非负结果；Math.abs(Long.MIN_VALUE) 仍是负数会污染末 7 位
        long seq = Math.floorMod(hash, SEQ_MOD);
        return prefix13 + String.format("%07d", seq);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** 返回本平台域前 10 位；未配置或非 10 位数字直接报错 */
    private String domainPrefix10() {
        String domain = properties.getDomain();
        if (domain == null || !domain.matches("\\d{10}")) {
            throw new ServiceException("gb28181.domain 未配置或不是 10 位数字，无法生成国标编码");
        }
        return domain;
    }

    /**
     * SHA-256 摘要截前 8 字节 → long。
     * <p>SHA-256 是 JDK 标准算法、无外部依赖、分布均匀；常规 {@code deviceId+channelId} 输入
     * ≤ 30 字符，单次计算纳秒级。截 8 字节作为 long 已经足够散列，无须 Murmur/SipHash 等额外依赖。</p>
     */
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
