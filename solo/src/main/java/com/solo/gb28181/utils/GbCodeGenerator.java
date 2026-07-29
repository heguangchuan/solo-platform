package com.solo.gb28181.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.entity.GbPlatformShare;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbPlatformShareMapper;
import com.solo.gb28181.mapper.GbGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * GB/T 28181-2016 附录 D 编码生成器。
 *
 * <p>20 位国标编码结构：{@code [8位行政区划][2位行业码][3位类型码][7位序号]}。前 10 位统一取本平台
 * {@link Gb28181Properties#getDomain()}（即本平台的行政区划中心编码），后 3 位类型码由业务实体决定，
 * 末 7 位序号在各自的作用域内递增（设备/分组：全平台；通道：同一设备）。</p>
 *
 * <p>本平台自建设备的 {@code deviceId} 前 10 位与 domain 一致，因此手动新增设备/通道/分组时都从
 * domain 取前缀，而不是从"某个已有设备的 deviceId 上再截一遍"。级联来源的设备/通道由 Catalog 同步
 * 决定，走不到这里。</p>
 *
 * <p>作用域约定：</p>
 * <ul>
 *   <li>设备/分组序号：{@code platform_id='0000'}（本平台自有数据）下同前缀取最大 +1。</li>
 *   <li>通道序号：同一 {@code device_db_id} 下同前缀取最大 +1（GB/T 28181 语义上通道号由物理
 *       设备自身分配，跨设备重码是正常情况，因此按设备分段）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GbCodeGenerator {

    /** 本平台自有数据的 platform_id 标识（与 sql/gb28181.sql 中默认值一致） */
    private static final String OWN_PLATFORM_ID = "0000";

    /** 设备类型码默认值：132 网络摄像机（IPC） */
    public static final String DEFAULT_DEVICE_TYPE = "132";
    /** 通道类型码默认值：131 摄像机 */
    public static final String DEFAULT_CHANNEL_TYPE = "131";
    /** 分组类型码默认值：215 业务分组（虚拟组织请显式传 216） */
    public static final String DEFAULT_GROUP_TYPE = "215";

    private final Gb28181Properties properties;
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbGroupMapper groupMapper;
    private final GbPlatformShareMapper platformShareMapper;

    /**
     * 生成下一个可用的设备 20 位国标 ID。
     *
     * @param typeCode 3 位类型码（118 NVR / 132 IPC / 200 网络视频服务器 …）；空则默认 {@link #DEFAULT_DEVICE_TYPE}
     */
    public String nextDeviceCode(String typeCode) {
        String prefix13 = domainPrefix10() + typeOrDefault(typeCode, DEFAULT_DEVICE_TYPE);
        LambdaQueryWrapper<GbDevice> wrapper = new LambdaQueryWrapper<GbDevice>()
                .eq(GbDevice::getPlatformId, OWN_PLATFORM_ID)
                .likeRight(GbDevice::getDeviceId, prefix13);
        int max = 0;
        for (GbDevice d : deviceMapper.selectList(wrapper)) {
            int seq = parseTailSeq(d.getDeviceId(), prefix13);
            if (seq > max) max = seq;
        }
        return prefix13 + pad7(max + 1);
    }

    /**
     * 生成下一个可用的资源目录分组 20 位国标 ID。
     *
     * @param typeCode 3 位类型码（215 业务分组 / 216 虚拟组织）；空则默认 {@link #DEFAULT_GROUP_TYPE}
     */
    public String nextGroupCode(String typeCode) {
        String prefix13 = domainPrefix10() + typeOrDefault(typeCode, DEFAULT_GROUP_TYPE);
        LambdaQueryWrapper<GbGroup> wrapper = new LambdaQueryWrapper<GbGroup>()
                .eq(GbGroup::getPlatformId, OWN_PLATFORM_ID)
                .likeRight(GbGroup::getGroupId, prefix13);
        int max = 0;
        for (GbGroup g : groupMapper.selectList(wrapper)) {
            int seq = parseTailSeq(g.getGroupId(), prefix13);
            if (seq > max) max = seq;
        }
        return prefix13 + pad7(max + 1);
    }

    /**
     * 生成指定设备下一个可用的通道 20 位国标 ID。
     *
     * <p>通道编码在所属设备内唯一（DB 层 {@code uk_device_channel(device_db_id, channel_id)} 兜底）。
     * GB/T 28181 语义上通道号由物理设备自身分配，跨设备重码是正常情况，因此序号扫描按 {@code deviceDbId}
     * 分段，只取同一设备下同类型码前缀的最大序号 +1；前 10 位仍取本平台 domain（自建设备的 deviceId
     * 前 10 位与 domain 天然一致）。级联来源设备的通道由 Catalog 同步入库，不走此路径。</p>
     *
     * @param deviceDbId 所属设备数据库主键（{@link GbDevice#getId()}）
     * @param typeCode   3 位类型码（131 摄像机 / 132 IPC …）；空则默认 {@link #DEFAULT_CHANNEL_TYPE}
     */
    public String nextChannelCode(Long deviceDbId, String typeCode) {
        if (deviceDbId == null) {
            throw new ServiceException("所属设备不能为空");
        }
        if (deviceMapper.selectById(deviceDbId) == null) {
            throw new ServiceException("所属设备不存在");
        }
        String prefix13 = domainPrefix10() + typeOrDefault(typeCode, DEFAULT_CHANNEL_TYPE);
        LambdaQueryWrapper<GbDeviceChannel> wrapper = new LambdaQueryWrapper<GbDeviceChannel>()
                .eq(GbDeviceChannel::getDeviceDbId, deviceDbId)
                .likeRight(GbDeviceChannel::getChannelId, prefix13);
        int max = 0;
        for (GbDeviceChannel c : channelMapper.selectList(wrapper)) {
            int seq = parseTailSeq(c.getChannelId(), prefix13);
            if (seq > max) max = seq;
        }
        return prefix13 + pad7(max + 1);
    }

    /**
     * 生成下一个可用的「对上级暴露」通道国标码（写入 {@code gb_platform_share.exposed_code}）。
     *
     * <p>通道原始 {@code channel_id} 只在设备内唯一，跨设备/级联透传可能撞码，会让上级点播认错通道。
     * 故对上级暴露时统一改用本平台命名空间内唯一的 20 位码：前 10 位本平台 domain + 3 位通道类型码
     * （131 摄像机）+ 7 位序号。序号在全表已用 exposed_code 上取同前缀最大值 +1，保证平台级唯一，
     * 且是合法 20 位纯数字（满足海康等上级校验）。</p>
     */
    public String nextExposedChannelCode() {
        return nextExposedChannelCodes(1).get(0);
    }

    /**
     * 批量生成 {@code count} 个连续的「对上级暴露」通道国标码。
     *
     * <p>只扫描一次全表已用 exposed_code 取同前缀最大序号，之后在内存里连续 +1 分配，
     * 避免调用方逐个通道触发全表扫描（N 通道 → N 次扫描的 O(n²)）。语义同 {@link #nextExposedChannelCode()}。</p>
     *
     * @param count 需要的码数量；&le;0 返回空列表
     */
    public List<String> nextExposedChannelCodes(int count) {
        if (count <= 0) return List.of();
        String prefix13 = domainPrefix10() + DEFAULT_CHANNEL_TYPE;
        LambdaQueryWrapper<GbPlatformShare> wrapper = new LambdaQueryWrapper<GbPlatformShare>()
                .eq(GbPlatformShare::getType, GbPlatformShare.TYPE_CHANNEL)
                .likeRight(GbPlatformShare::getExposedCode, prefix13)
                .select(GbPlatformShare::getExposedCode);
        int max = 0;
        for (GbPlatformShare share : platformShareMapper.selectList(wrapper)) {
            int seq = parseTailSeq(share.getExposedCode(), prefix13);
            if (seq > max) max = seq;
        }
        List<String> codes = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            codes.add(prefix13 + pad7(max + i));
        }
        return codes;
    }

    /** 返回本平台域前 10 位；未配置或非 10 位数字直接报错 */
    public String domainPrefix10() {
        String domain = properties.getDomain();
        if (domain == null || !domain.matches("\\d{10}")) {
            throw new ServiceException("gb28181.domain 未配置或不是 10 位数字，无法生成国标编码");
        }
        return domain;
    }

    /** 校验并规范化 3 位类型码，空/非法值回落到 fallback（fallback 由调用方保证合法） */
    private static String typeOrDefault(String typeCode, String fallback) {
        if (typeCode == null || typeCode.isBlank()) return fallback;
        String t = typeCode.trim();
        if (!t.matches("\\d{3}")) {
            throw new ServiceException("类型码必须为 3 位数字：" + typeCode);
        }
        return t;
    }

    /** 从 20 位编码里取末 7 位序号；前缀不匹配或长度非 20 返回 0 */
    private static int parseTailSeq(String code, String prefix13) {
        if (code == null || code.length() != 20 || !code.startsWith(prefix13)) return 0;
        try {
            return Integer.parseInt(code.substring(13));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String pad7(int seq) {
        return String.format("%07d", seq);
    }
}
