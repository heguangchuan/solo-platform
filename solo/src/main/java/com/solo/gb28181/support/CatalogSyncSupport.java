package com.solo.gb28181.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.constant.GbSource;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Catalog 同步落库执行器（异步批量写，ADR-023）。
 * <p>由 {@link com.solo.gb28181.processor.request.message.response.CatalogDataCache}
 * 在阈值/收尾时调 {@link #executeFlush} 提交一次 flush 任务。任务在
 * {@code catalogSyncExecutor} 上跑，DB 写不占用 SIP 消息分发线程。</p>
 *
 * <h3>合并后的数据流</h3>
 * <p>分组（parental=1）与通道（parental=0）落到同一张 {@code gb_device_channel}，靠 parental 区分。
 * item 的 {@code parent_id} 直接是父 item 的 20 位国标码（与父行 {@code channel_id} 对齐），
 * 字符串类型，{@code "0"} 表根（无父）。本类不再做 GB 码 ↔ 雪花 id 转换。</p>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>中间 flush：直接调 {@code channelService.saveOrUpdateBatch(items)}，单 SQL upsert。</li>
 *   <li>final flush：额外做两件事——
 *     <ul>
 *       <li><b>孤儿 parentId 回落</b>：item 的 {@code parent_id}（GB 码）在库/本批都找不到 →
 *           改挂 {@code device.getDeviceId()}，让目录树有唯一收口（中间 flush 不做，避免"先报子后报父"误伤）</li>
 *       <li>若 {@code completenessRatio >= threshold} 清扫"已不存在通道"（仅 parental=0 行），
 *           否则保留以备下次 sync 补齐</li>
 *     </ul>
 *   </li>
 * </ol>
 */
@Slf4j
@Service
public class CatalogSyncSupport {

    /** 根哨兵——parent_id == "0" 表无父 */
    private static final String ROOT_PARENT = "0";

    private final Gb28181Properties properties;
    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;
    private final ThreadPoolTaskExecutor executor;

    private final AtomicLong totalFlushCount = new AtomicLong(0);
    private final AtomicLong totalFlushedItems = new AtomicLong(0);

    /**
     * 显式构造器——Lombok @RequiredArgsConstructor 不会把字段上的 @Qualifier 复制到构造器参数上，
     * 这里手写保证 executor 按 bean 名称注入。
     */
    public CatalogSyncSupport(Gb28181Properties properties,
                              GbDeviceService deviceService,
                              GbDeviceChannelService channelService,
                              @Qualifier("catalogSyncExecutor") ThreadPoolTaskExecutor executor) {
        this.properties = properties;
        this.deviceService = deviceService;
        this.channelService = channelService;
        this.executor = executor;
    }

    /**
     * 提交一次 flush 任务到 catalogSyncExecutor。
     */
    public void executeFlush(CatalogSyncContext ctx, List<GbDeviceChannel> items, boolean isFinalFlush) {
        if (ctx == null) return;
        // 队列前再 check 一次：cancel 已发生（done=true）→ 不入队
        // ⚠️ 但 final flush 必须入队——finalize 已设 done=true，这里再 check 会自误，
        //   导致 sumNum 收齐时 final flush 被自己的 done=true 拦截、Item 永远不落库。
        //   intermediate flush（accumulate 触发的）依然走 done check 尊重 cancel。
        if (!isFinalFlush && ctx.done) {
            log.info("[通道目录] flush 被取消（入队前） deviceId={} sn={} items={}",
                    ctx.deviceId, ctx.sn, items.size());
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
                doFlush(ctx, items, isFinalFlush);
                long cost = System.currentTimeMillis() - start;
                totalFlushCount.incrementAndGet();
                totalFlushedItems.addAndGet(items.size());
                log.info("[通道目录] flush 完成 deviceId={} sn={} items={} final={} costMs={} (累计 flush={} items={})",
                        ctx.deviceId, ctx.sn, items.size(), isFinalFlush, cost,
                        totalFlushCount.get(), totalFlushedItems.get());
            } catch (Exception e) {
                log.error("[通道目录] flush 失败 deviceId={} sn={} final={} err={}",
                        ctx.deviceId, ctx.sn, isFinalFlush, e.getMessage(), e);
            }
        });
    }

    private void doFlush(CatalogSyncContext ctx, List<GbDeviceChannel> items, boolean isFinalFlush) {
        GbDevice device = deviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, ctx.deviceId)
                .one();
        if (device == null) {
            log.warn("[通道目录] flush 跳过：设备不存在 deviceId={}", ctx.deviceId);
            return;
        }

        // 入库前预处理
        if (!items.isEmpty()) {
            // 预填 id（雪花）—— batchUpsert 走业务唯一键 uk_device_channel；分组在 service
            // 层 saveOrUpdateBatch 已按 channel_id 应用层查重复用 id（无 DB 唯一键）。
            for (GbDeviceChannel it : items) {
                if (it.getId() == null) {
                    it.setId(IdWorker.getId());
                }
                // 源统一 REGISTER（合并后不再区分级联，ADR-016 决策点）
                it.setSource(GbSource.REGISTER);
            }

            // final flush 阶段做"孤儿 parentId 回落"：parent_id（GB 码）在库里/本批里都找不到
            // （设备漏报父节点、报了非法 parent_id、或解析为空被 handler 兜底成设备 GB 码），
            // 一律改挂到 device.deviceId 下——让目录树有唯一收口，不出现游离顶层节点。
            // 中间 flush 不做：设备可能先报子、后报父，那时判定会误伤。
            if (isFinalFlush) {
                Set<String> knownCodes = new HashSet<>();
                // 本批已见过的 channel_id 算"父可达"
                for (GbDeviceChannel it : items) {
                    if (it.getChannelId() != null) knownCodes.add(it.getChannelId());
                }
                // 库里已存在的 channel_id 也算"父可达"（本次没重报但历史已入库）
                channelService.lambdaQuery()
                        .select(GbDeviceChannel::getChannelId)
                        .list()
                        .forEach(e -> { if (e.getChannelId() != null) knownCodes.add(e.getChannelId()); });

                int reparented = 0;
                for (GbDeviceChannel it : items) {
                    String pid = it.getParentId();
                    if (ROOT_PARENT.equals(pid) || (pid != null && knownCodes.contains(pid))) {
                        continue;
                    }
                    it.setParentId(device.getDeviceId());
                    reparented++;
                }
                if (reparented > 0) {
                    log.info("[通道目录] 孤儿 parentId 回落到设备节点 deviceId={} count={}/{}",
                            device.getDeviceId(), reparented, items.size());
                }
            }

            channelService.saveOrUpdateBatch(items);
        }

        // final flush 阶段：依据 completenessRatio 决定是否清理"已不存在通道"
        if (isFinalFlush) {
            double ratio = ctx.completenessRatio;
            double threshold = properties.getCatalog().getCompletenessThreshold();
            int countAfter = Math.toIntExact(channelService.lambdaQuery()
                    .eq(GbDeviceChannel::getDeviceId, device.getDeviceId())
                    .eq(GbDeviceChannel::getParental, 0)
                    .count());
            if (ratio >= threshold) {
                boolean ok = channelService.lambdaUpdate()
                        .eq(GbDeviceChannel::getDeviceId, device.getDeviceId())
                        .eq(GbDeviceChannel::getParental, 0)
                        .notIn(GbDeviceChannel::getChannelId, ctx.syncedItemIds)
                        .remove();
                log.info("[通道目录] final flush 清扫 deviceId={} ratio={} threshold={} (传入 {} 条)  ok={}",
                        ctx.deviceId, String.format("%.2f", ratio), threshold,
                        ctx.syncedItemIds.size(), ok);
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
