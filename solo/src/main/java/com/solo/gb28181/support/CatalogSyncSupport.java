package com.solo.gb28181.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.constant.GbSource;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Catalog 同步落库执行器（异步批量写）。
 * <p>由 {@link com.solo.gb28181.processor.request.message.response.CatalogDataCache}
 * 在阈值/收尾时调 {@link #executeFlush} 提交一次 flush 任务。任务在
 * {@code catalogSyncExecutor} 上跑，DB 写不占用 SIP 消息分发线程。</p>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>中间 flush：直接调 {@code channelService.saveOrUpdateBatch(channels)} +
 *       {@code gbGroupService.saveOrUpdateBatch(groups)}。两者都已走批量 upsert。</li>
 *   <li>final flush：额外做三件事——
 *     <ul>
 *       <li><b>孤儿分组回落</b>：{@code parent_id} 指向的 group code 在库里/本批里都找不到 →
 *           改挂 {@code device_id}，让分组树有唯一收口（中间 flush 不做，避免"先报子后报父"误伤）</li>
 *       <li>解析 ParentID(GB码) → 父 group 雪花 id，回填通道的 {@code group_id}</li>
 *       <li>若 {@code completenessRatio >= threshold} 清扫"已不存在通道"，否则保留以备下次 sync 补齐</li>
 *     </ul>
 *   </li>
 * </ol>
 */
@Slf4j
@Service
public class CatalogSyncSupport {

    private final Gb28181Properties properties;
    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;
    private final GbGroupService gbGroupService;
    private final ThreadPoolTaskExecutor executor;

    private final AtomicLong totalFlushCount = new AtomicLong(0);
    private final AtomicLong totalFlushedChannels = new AtomicLong(0);
    private final AtomicLong totalFlushedGroups = new AtomicLong(0);

    /**
     * 显式构造器——Lombok @RequiredArgsConstructor 不会把字段上的 @Qualifier 复制到构造器参数上，
     * 这里手写保证 executor 按 bean 名称注入。
     */
    public CatalogSyncSupport(Gb28181Properties properties,
                              GbDeviceService deviceService,
                              GbDeviceChannelService channelService,
                              GbGroupService gbGroupService,
                              @Qualifier("catalogSyncExecutor") ThreadPoolTaskExecutor executor) {
        this.properties = properties;
        this.deviceService = deviceService;
        this.channelService = channelService;
        this.gbGroupService = gbGroupService;
        this.executor = executor;
    }

    /**
     * 提交一次 flush 任务到 catalogSyncExecutor。
     */
    public void executeFlush(CatalogSyncContext ctx,
                             List<GbDeviceChannel> channels,
                             List<GbGroup> groups,
                             Map<String, String> channelParent,
                             boolean isFinalFlush) {
        if (ctx == null) return;
        // 队列前再 check 一次：cancel 已发生（done=true）→ 不入队
        // ⚠️ 但 final flush 必须入队——finalize 已设 done=true，这里再 check 会自误，
        //   导致 sumNum 收齐时 final flush 被自己的 done=true 拦截、Item 永远不落库。
        //   intermediate flush（accumulate 触发的）依然走 done check 尊重 cancel。
        if (!isFinalFlush && ctx.done) {
            log.info("[通道目录] flush 被取消（入队前） deviceId={} sn={} channels={} groups={}",
                    ctx.deviceId, ctx.sn, channels.size(), groups.size());
            return;
        }
        executor.execute(() -> {
            // 队列中、运行前的窗口：cancel 可能在这一刻发生，再 check 一次
            // 同上：final flush 不受 done 拦截
            if (!isFinalFlush && ctx.done) {
                log.info("[通道目录] flush 被取消（运行前） deviceId={} sn={}", ctx.deviceId, ctx.sn);
                return;
            }
            long start = System.currentTimeMillis();
            try {
                doFlush(ctx, channels, groups, channelParent, isFinalFlush);
                long cost = System.currentTimeMillis() - start;
                totalFlushCount.incrementAndGet();
                totalFlushedChannels.addAndGet(channels.size());
                totalFlushedGroups.addAndGet(groups.size());
                log.info("[通道目录] flush 完成 deviceId={} sn={} channels={} groups={} final={} costMs={} (累计 flush={} ch={} g={})",
                        ctx.deviceId, ctx.sn, channels.size(), groups.size(), isFinalFlush, cost,
                        totalFlushCount.get(), totalFlushedChannels.get(), totalFlushedGroups.get());
            } catch (Exception e) {
                log.error("[通道目录] flush 失败 deviceId={} sn={} final={} err={}",
                        ctx.deviceId, ctx.sn, isFinalFlush, e.getMessage(), e);
            }
        });
    }

    private void doFlush(CatalogSyncContext ctx,
                         List<GbDeviceChannel> channels,
                         List<GbGroup> groups,
                         Map<String, String> channelParent,
                         boolean isFinalFlush) {
        GbDevice device = deviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, ctx.deviceId)
                .one();
        if (device == null) {
            log.warn("[通道目录] flush 跳过：设备不存在 deviceId={}", ctx.deviceId);
            return;
        }
        // 设备与下级平台不再区分（gb_platform 已翻转为黑名单，没有"哪些是平台"的登记）：
        // 不再自动建平台根目录 —— 孤儿分组统一回落到 device_id（见下方 isFinalFlush 分支），
        // source 统一 REGISTER，platform_id 三列已从表里删除。

        // 目录节点入库
        Map<String, Long> codeToId = new HashMap<>();
        if (!groups.isEmpty()) {
            Set<String> codes = new HashSet<>();
            for (GbGroup g : groups) codes.add(g.getGroupId());
            Map<String, Long> existingIdByCode = new HashMap<>();
            gbGroupService.lambdaQuery().in(GbGroup::getGroupId, codes).list()
                    .forEach(e -> existingIdByCode.put(e.getGroupId(), e.getId()));

            for (GbGroup g : groups) {
                Long id = existingIdByCode.get(g.getGroupId());
                if (id == null) {
                    id = IdWorker.getId();
                }
                g.setId(id);
                codeToId.put(g.getGroupId(), id);
            }

            // final flush 阶段做"孤儿回落"：parent_id 指向的 group code 在库里/本批里都找不到
            // （设备漏报父节点、报了非法 parent、或 <ParentID> 空被 handler 兜底成 device_id），
            // 一律改挂到 device_id 下——让分组树有唯一收口，不出现游离顶层节点。
            // 中间 flush 不做：设备可能先报子、后报父，那时判定会误伤。
            if (isFinalFlush) {
                Set<String> knownCodes = new HashSet<>(codeToId.keySet());
                // 库里已存在的分组 code 也算"父可达"（本次没重报但历史已入库）
                gbGroupService.lambdaQuery()
                        .select(GbGroup::getGroupId)
                        .list()
                        .forEach(e -> knownCodes.add(e.getGroupId()));
                int reparented = 0;
                for (GbGroup g : groups) {
                    String pid = g.getParentId();
                    // "0" 是平台根自己的哨兵值，不动；父可达的不动
                    if ("0".equals(pid) || (pid != null && knownCodes.contains(pid))) {
                        continue;
                    }
                    g.setParentId(device.getDeviceId());
                    reparented++;
                }
                if (reparented > 0) {
                    log.info("[通道目录] 孤儿分组回落到设备节点 deviceId={} count={}/{}",
                            device.getDeviceId(), reparented, groups.size());
                }
            }

            gbGroupService.saveOrUpdateBatch(groups);
        }

        // 通道入库：source 统一 REGISTER（不再区分级联）；
        // final flush 额外按 channelParent 解析 groupId，解析不到就保持默认分组。
        if (!channels.isEmpty()) {
            for (GbDeviceChannel ch : channels) {
                ch.setSource(GbSource.REGISTER);
            }
            if (isFinalFlush && channelParent != null) {
                for (GbDeviceChannel ch : channels) {
                    String parentCode = channelParent.get(ch.getChannelId());
                    Long parentDbId = parentCode == null ? null : codeToId.get(parentCode);
                    if (parentDbId != null) {
                        ch.setGroupId(parentDbId);
                    }
                    // 解析不到父分组：不动 group_id，由 DB 默认值（默认分组 id=0）兜底
                }
            }
            channelService.saveOrUpdateBatch(channels);
        }

        // final flush 阶段：依据 completenessRatio 决定是否清理"已不存在通道"
        if (isFinalFlush) {
            double ratio = ctx.completenessRatio;
            double threshold = properties.getCatalog().getCompletenessThreshold();
            int countAfter = Math.toIntExact(channelService.lambdaQuery()
                    .eq(GbDeviceChannel::getDeviceDbId, device.getId()).count());
            if (ratio >= threshold) {
                boolean ok = channelService.lambdaUpdate()
                        .eq(GbDeviceChannel::getDeviceDbId, device.getId())
                        .notIn(GbDeviceChannel::getChannelId, ctx.syncedChannelIds)
                        .remove();
                log.info("[通道目录] final flush 清扫 deviceId={} ratio={} threshold={} (传入 {} 条)  ok={}",
                        ctx.deviceId, String.format("%.2f", ratio), threshold,
                        ctx.syncedChannelIds.size(), ok);
            } else {
                log.warn("[通道目录] final flush 保留残缺数据 deviceId={} ratio={} < threshold={} 保留 {} 条已入库通道，等下次 sync 补齐",
                        ctx.deviceId, String.format("%.2f", ratio), threshold, countAfter);
            }
            // 落库后回写 device.channelCount 与 lastCatalogSyncTime
            device.setChannelCount(countAfter);
            device.setLastCatalogSyncTime(LocalDateTime.now());
            deviceService.updateById(device);
            // 失效档案缓存：channelCount / lastCatalogSyncTime 变了，下条 MESSAGE 应拿到最新值
            deviceService.evictProfileCache(device.getDeviceId());
        }
    }
}
