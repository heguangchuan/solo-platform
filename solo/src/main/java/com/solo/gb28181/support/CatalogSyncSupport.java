package com.solo.gb28181.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.constant.GbSource;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbGroupService;
import com.solo.gb28181.service.GbPlatformService;
import com.solo.gb28181.utils.SipUtils;
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
 *   <li>final flush：额外解析 ParentID(GB码) → 父 group 雪花 id，
 *       落库后若 {@code completenessRatio >= threshold} 清扫"已不存在通道"，
 *       否则保留以备下次 sync 补齐。</li>
 * </ol>
 */
@Slf4j
@Service
public class CatalogSyncSupport {

    private final Gb28181Properties properties;
    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;
    private final GbGroupService gbGroupService;
    private final GbPlatformService gbPlatformService;
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
                              GbPlatformService gbPlatformService,
                              @Qualifier("catalogSyncExecutor") ThreadPoolTaskExecutor executor) {
        this.properties = properties;
        this.deviceService = deviceService;
        this.channelService = channelService;
        this.gbGroupService = gbGroupService;
        this.gbPlatformService = gbPlatformService;
        this.executor = executor;
    }

    /**
     * 提交一次 flush 任务到 catalogSyncExecutor。
     */
    public void executeFlush(CatalogSyncContext ctx,
                             List<GbDeviceChannel> channels,
                             List<GbGroup> groups,
                             Map<String, String> channelParent,
                             Map<String, String> groupParent,
                             boolean isFinalFlush) {
        if (ctx == null) return;
        executor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                doFlush(ctx, channels, groups, channelParent, groupParent, isFinalFlush);
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
                         Map<String, String> groupParent,
                         boolean isFinalFlush) {
        GbDevice device = deviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, ctx.deviceId)
                .one();
        if (device == null) {
            log.warn("[通道目录] flush 跳过：设备不存在 deviceId={}", ctx.deviceId);
            return;
        }
        boolean isPlatform = gbPlatformService.lambdaQuery()
                .eq(GbPlatform::getPlatformId, device.getDeviceId())
                .eq(GbPlatform::getEnabled, 1)
                .exists();

        // 平台根目录（仅 final flush 阶段创建，作为兜底挂载点）
        String platformRootCode = null;
        Long platformRootId = null;
        if (isPlatform && isFinalFlush) {
            GbPlatform platform = gbPlatformService.lambdaQuery()
                    .eq(GbPlatform::getPlatformId, device.getDeviceId())
                    .one();
            platformRootCode = SipUtils.toBusinessGroupId(device.getDeviceId());
            String rootName = (platform != null && platform.getName() != null && !platform.getPlatformId().isBlank())
                    ? platform.getName() : device.getDeviceId();
            GbGroup root = new GbGroup();
            root.setGroupId(platformRootCode);
            root.setName(rootName);
            root.setPlatformId(device.getPlatformId());
            root.setSort(1);
            groups.addFirst(root);
        }

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
            if (isFinalFlush && groupParent != null) {
                if (platformRootCode != null) {
                    platformRootId = codeToId.get(platformRootCode);
                }
                for (GbGroup g : groups) {
                    if (platformRootCode != null && platformRootCode.equals(g.getGroupId())) {
                        g.setParentId(0L);
                        continue;
                    }
                    String parentCode = groupParent.get(g.getGroupId());
                    Long parentDbId = parentCode == null ? null : codeToId.get(parentCode);
                    if (parentDbId != null) {
                        g.setParentId(parentDbId);
                    } else if (isPlatform && platformRootId != null) {
                        g.setParentId(platformRootId);
                    } else {
                        g.setParentId(0L);
                    }
                }
            }
            gbGroupService.saveOrUpdateBatch(groups);
        }

        // 通道入库：所有 flush 阶段都设 platformId + source（不依赖 ParentID 解析），
        // final flush 额外按 channelParent 解析 groupId。
        if (!channels.isEmpty()) {
            for (GbDeviceChannel ch : channels) {
                ch.setPlatformId(device.getPlatformId());
                ch.setSource(isPlatform ? GbSource.CASCADE : GbSource.REGISTER);
            }
            if (isFinalFlush && channelParent != null) {
                if (platformRootId == null && platformRootCode != null) {
                    platformRootId = codeToId.get(platformRootCode);
                }
                for (GbDeviceChannel ch : channels) {
                    String parentCode = channelParent.get(ch.getChannelId());
                    Long parentDbId = parentCode == null ? null : codeToId.get(parentCode);
                    if (parentDbId != null) {
                        ch.setGroupId(parentDbId);
                    } else if (isPlatform && platformRootId != null) {
                        ch.setGroupId(platformRootId);
                    }
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
