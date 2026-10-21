package com.solo.media.ssrc;

import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.properties.MediaProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SsrcFactory {

    private static final int SLOT_COUNT = 9999;

    /**
     * Redis key 前缀：完整 key = {@code media:ssrc:pool:{serverId}}
     */
    private static final String POOL_KEY_PREFIX = "media:ssrc:pool:";

    private final Gb28181Properties sipProperties;
    private final MediaProperties mediaProperties;
    private final GbInviteSessionService inviteSessionService;
    private final GbMediaServerMapper mediaServerMapper;
    private final StringRedisTemplate redis;

    /**
     * SSRC 前缀（D + HHHHH，共 6 位）— 跨节点共享
     */
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

        // 启动时为每个已登记 ZLM 预建池（防御性兜底；正常情况下池已存在）
        List<MediaServer> servers = mediaServerMapper.selectList(null);
        for (MediaServer s : servers) {
            initPoolIfMissing(s.getServerId());
        }

        // 把 Redis 中残留的活会话 SSRC 从池中剔除（防重号；与原 BitSet 行为一致）
        List<GbInviteSession> active = inviteSessionService.listAllActive();
        int reclaimed = 0;
        for (GbInviteSession s : active) {
            if (s.getServerId() == null || s.getSsrc() == null) {
                continue;
            }
            Integer slot = parseSlot(s.getSsrc());
            if (slot != null) {
                redis.opsForSet().remove(POOL_KEY_PREFIX + s.getServerId(), String.format("%04d", slot));
                reclaimed++;
            }
        }
        log.info("[流媒体] SsrcFactory 启动 prefix={} 已登记 ZLM={} 已预占 SSRC={} 条",
                prefix, servers.size(), reclaimed);
    }

    public synchronized String acquire(String serverId, boolean isPlayback) {
        if (serverId == null || serverId.isBlank()) {
            throw new ServiceException("serverId 不能为空");
        }
        initPoolIfMissing(serverId);
        String key = POOL_KEY_PREFIX + serverId;

        String slot = redis.opsForSet().pop(key);
        if (slot == null) {
            // 兜底：池耗尽 / 被人为清空，自动重灌一次
            log.error("[SSRC池] {} 已耗尽或不存在，自动重灌以恢复服务", key);
            initPoolByKey(key);
            slot = redis.opsForSet().pop(key);
            if (slot == null) {
                throw new ServiceException("SSRC 池重灌后仍无法分配 serverId=" + serverId);
            }
        }
        return formatSsrc(isPlayback, slot);
    }

    public synchronized void release(String serverId, String ssrc) {
        if (serverId == null || ssrc == null) {
            return;
        }
        Integer slot = parseSlot(ssrc);
        if (slot == null) {
            return; // 非本平台命名空间（下级自定义 SSRC），见 ADR-025
        }
        redis.opsForSet().add(POOL_KEY_PREFIX + serverId, String.format("%04d", slot));
    }

    private void initPoolIfMissing(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        String key = POOL_KEY_PREFIX + serverId;
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        initPoolByKey(key);
    }

    /**
     * 强制重灌（清空后写满 0001..9999）。仅在池耗尽/丢失时调用。
     */
    private void initPoolByKey(String key) {
        List<String> members = new ArrayList<>(SLOT_COUNT);
        for (int i = 1; i <= SLOT_COUNT; i++) {
            members.add(String.format("%04d", i));
        }
        redis.delete(key);
        redis.opsForSet().add(key, members.toArray(new String[0]));
    }

    private String formatSsrc(boolean isPlayback, String slot4) {
        char d = isPlayback ? '1' : '0';
        String middle = prefix.substring(1); // 去掉默认 D
        return d + middle + slot4;
    }

    /**
     * 解析<b>本平台自己分配的</b> SSRC 的序号槽位；不是本平台分配的一律返回 null。
     *
     * <p>必须校验中段（第 2-6 位）与本平台 {@code prefix} 一致：下级级联平台会自报它自己的 SSRC
     * （如 {@code 0301464943}，中段取自它自己的域），这个值会被写回会话记录，之后清扫时
     * 照样会调 {@link #release}。若不校验中段就按 {@code substring(6)} 取槽位，
     * 会把一个从未分配过、甚至正被其它流占用的槽位错误清零，
     * 后续 {@link #acquire} 就会发出重复 SSRC。</p>
     */
    private Integer parseSlot(String ssrc) {
        if (ssrc == null || ssrc.length() != 10) {
            return null;
        }
        String p = prefix;
        if (p == null || p.length() != 6 || !ssrc.regionMatches(1, p, 1, 5)) {
            return null; // 非本平台命名空间（下级自定义 SSRC）
        }
        try {
            int slot = Integer.parseInt(ssrc.substring(6));
            return (slot >= 1 && slot <= SLOT_COUNT) ? slot : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
