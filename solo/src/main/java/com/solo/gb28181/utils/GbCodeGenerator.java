package com.solo.gb28181.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbPlatformShare;
import com.solo.gb28181.mapper.GbPlatformShareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 「对上级暴露」通道国标码生成器（GB/T 28181-2016 附录 D 编码结构）。
 *
 * <p>20 位国标编码结构：{@code [8位行政区划][2位行业码][3位类型码][7位序号]}。前 10 位统一取本平台
 * {@link Gb28181Properties#getDomain()}（即本平台的行政区划中心编码），后 3 位固定通道类型码，
 * 末 7 位序号在全表已用 {@code exposed_code} 上递增。</p>
 *
 * <p><b>只服务级联共享场景</b>。设备 / 通道的国标编码一律由运营在管理面手工填入，
 * 不再由平台自动生成——自动生成会与设备侧真实编码规则冲突（GB/T 28181 语义上编码由
 * 物理设备自身分配），且给运营造成「系统已经算好了」的错觉。原先的
 * {@code nextDeviceCode} / {@code nextChannelCode} / {@code nextGroupCode}
 * 与配套的 {@code /api/gb28181/code/*} 端点已移除。</p>
 */
@Component
@RequiredArgsConstructor
public class GbCodeGenerator {

    /** 通道类型码：131 摄像机 */
    private static final String CHANNEL_TYPE = "131";

    private final Gb28181Properties properties;
    private final GbPlatformShareMapper platformShareMapper;

    /**
     * 批量生成 {@code count} 个连续的「对上级暴露」通道国标码（写入 {@code gb_platform_share.exposed_code}）。
     *
     * <p>通道原始 {@code channel_id} 只在设备内唯一，跨设备/级联透传可能撞码，会让上级点播认错通道。
     * 故对上级暴露时统一改用本平台命名空间内唯一的 20 位码：前 10 位本平台 domain + 3 位通道类型码
     * （131 摄像机）+ 7 位序号。序号在全表已用 exposed_code 上取同前缀最大值 +1，保证平台级唯一，
     * 且是合法 20 位纯数字（满足海康等上级校验）。</p>
     *
     * <p>只扫描一次全表已用 exposed_code 取同前缀最大序号，之后在内存里连续 +1 分配，
     * 避免调用方逐个通道触发全表扫描（N 通道 → N 次扫描的 O(n²)）。</p>
     *
     * @param count 需要的码数量；&le;0 返回空列表
     */
    public List<String> nextExposedChannelCodes(int count) {
        if (count <= 0) return List.of();
        String prefix13 = domainPrefix10() + CHANNEL_TYPE;
        LambdaQueryWrapper<GbPlatformShare> wrapper = new LambdaQueryWrapper<GbPlatformShare>()
                .isNotNull(GbPlatformShare::getExposedCode)
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
    private String domainPrefix10() {
        String domain = properties.getDomain();
        if (domain == null || !domain.matches("\\d{10}")) {
            throw new ServiceException("gb28181.domain 未配置或不是 10 位数字，无法生成国标编码");
        }
        return domain;
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
