package com.solo.gb28181.media.ssrc;

import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.config.MediaProperties;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbInviteSessionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSRC 池：按 ZLM 节点分桶维护占用情况。
 *
 * <h3>SSRC 国标规则（GB/T 28181-2016 D.1）</h3>
 * <p>10 位十进制字符串，结构 {@code D HHHHH NNNN}：
 * <ul>
 *     <li>D：1 位，0=实时视频，1=历史回放</li>
 *     <li>HHHHH：5 位，SIP 域中段（取平台域的 3-7 位，{@code 6000000004} 取 {@code 00000}；不足左补 0）</li>
 *     <li>NNNN：4 位，序号 0001-9999</li>
 * </ul>
 *
 * <p>同节点 SSRC 必须全局唯一，否则 ZLM 会拒绝下一次 openRtpServer。
 * 释放时机：closeRtpServer 成功之后再 release，**严禁先 release 再 closeRtpServer**。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsrcFactory {

    /** SSRC 序号位宽 */
    private static final int SLOT_COUNT = 9999;

    private final Gb28181Properties sipProperties;
    private final MediaProperties mediaProperties;
    private final GbInviteSessionService inviteSessionService;

    /** key=serverId，value=占用位图（下标 1..9999 表示序号占用） */
    private final Map<String, BitSet> pool = new ConcurrentHashMap<>();

    /** SSRC 前缀（D + HHHHH，共 6 位）— 跨节点共享 */
    private volatile String prefix;

    @PostConstruct
    void init() {
        // 取 SIP 域中段 5 位作为 SSRC 前缀 HHHHH 部分
        String domain = sipProperties.getDomain() == null ? "" : sipProperties.getDomain();
        String middle = "00000";
        if (domain.length() >= 7) {
            middle = domain.substring(2, 7);
        } else if (domain.length() >= 2) {
            String tail = domain.substring(2);
            middle = ("00000" + tail).substring(tail.length()); // 左补 0
        }
        int d = mediaProperties.getSsrcDomainStart() == 1 ? 1 : 0; // 0=视频，1=回放（先以视频为默认）
        this.prefix = d + middle;

        // 启动时把 Redis 中残留的活会话 SSRC 标记为已占用（防御性兜底；GbPlayServiceImpl
        // 的 @PostConstruct 通常会先清扫掉所有残留会话，这里看到的多半是空集）
        List<GbInviteSession> active = inviteSessionService.listAllActive();
        for (GbInviteSession s : active) {
            try {
                Integer slot = parseSlot(s.getSsrc());
                if (slot != null) {
                    pool.computeIfAbsent(s.getServerId(), k -> new BitSet(SLOT_COUNT + 1)).set(slot);
                }
            } catch (Exception ignore) {
                // ssrc 格式异常忽略
            }
        }
        log.info("[流媒体] SsrcFactory 启动 prefix={} 已占用会话数={}", prefix, active.size());
    }

    /**
     * 申请一个 SSRC
     *
     * @param serverId  ZLM 节点
     * @param isPlayback 是否回放（影响第 1 位 D）
     */
    public synchronized String acquire(String serverId, boolean isPlayback) {
        BitSet bits = pool.computeIfAbsent(serverId, k -> new BitSet(SLOT_COUNT + 1));
        // 1..9999 找一个未占用槽位
        for (int slot = 1; slot <= SLOT_COUNT; slot++) {
            if (!bits.get(slot)) {
                bits.set(slot);
                return formatSsrc(isPlayback, slot);
            }
        }
        throw new ServiceException("SSRC 池已用尽 serverId=" + serverId);
    }

    /**
     * 释放 SSRC
     */
    public synchronized void release(String serverId, String ssrc) {
        if (serverId == null || ssrc == null) {
            return;
        }
        BitSet bits = pool.get(serverId);
        if (bits == null) {
            return;
        }
        Integer slot = parseSlot(ssrc);
        if (slot != null) {
            bits.clear(slot);
        }
    }

    private String formatSsrc(boolean isPlayback, int slot) {
        char d = isPlayback ? '1' : '0';
        String middle = prefix.substring(1); // 去掉默认 D
        return d + middle + String.format("%04d", slot);
    }

    private static Integer parseSlot(String ssrc) {
        if (ssrc == null || ssrc.length() != 10) {
            return null;
        }
        try {
            return Integer.parseInt(ssrc.substring(6));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
