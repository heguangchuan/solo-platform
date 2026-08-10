package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.support.CatalogSyncContext;
import com.solo.gb28181.support.CatalogSyncProgress;
import com.solo.gb28181.support.CatalogSyncSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalog 分片缓冲（10w+ 通道场景专用）。
 * <p>原"接收即落库"模型：所有 Item 堆在 {@code List<GbDeviceChannel>}
 * 里等 {@code sumNum} 收齐才一次性入库，4w 通道即 OOM。改为：</p>
 * <ul>
 *   <li>每个设备维护一个 {@link CatalogSyncContext}，包含滚动缓冲 + 累计计数 + 重传去重 Set</li>
 *   <li>每收到一个分片就 {@code accumulate}；缓冲达到 {@code flushThreshold}（默认 1000）即
 *       提交一次 flush 到 {@code catalogSyncExecutor} 异步落库，落完清空缓冲</li>
 *   <li>累计数（{@code totalReceived}）独立于缓冲，不随 flush 清零，用于判定收齐与暴露进度</li>
 *   <li>完成判定：{@code totalReceived >= sumNum} → 自动 {@code finalFlush} 收尾；
 *       收不到 SumNum 的兜底：{@link #sweep()} 每 30s 扫一次，超 {@code expireMs} 强收</li>
 *   <li>收尾时若达到 {@code completenessThreshold}（默认 0.95）才删"已不存在通道"，否则保留</li>
 * </ul>
 *
 * <p>本类只负责"在内存里攒"+"触发 flush"；具体落库由 {@link CatalogSyncSupport} 在 catalogSyncExecutor
 * 上执行，逻辑跟"分片并发落库"解耦。</p>
 */
@Slf4j
@Component
public class CatalogDataCache {

    private final Gb28181Properties properties;
    private final CatalogSyncSupport syncSupport;
    private final ApplicationEventPublisher eventPublisher;

    /** 当前进行中的同步上下文，deviceId → context。 */
    private final Map<String, CatalogSyncContext> contexts = new ConcurrentHashMap<>();

    public CatalogDataCache(Gb28181Properties properties,
                            CatalogSyncSupport syncSupport,
                            ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.syncSupport = syncSupport;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 取消某设备的进行中同步（设备下线 / 重新上线前调）。
     * <p>行为：持锁将 context 标记为 done（挡住任何 in-flight 的 accumulate 继续写缓冲），
     * 并从 {@link #contexts} 移除。已入队到 {@code catalogSyncExecutor} 的 flush 任务由
     * {@link com.solo.gb28181.support.CatalogSyncSupport#executeFlush} 的 done 检查拦截，
     * 避免 cancel 后仍有部分数据落库。</p>
     * <p><b>不</b>调 finalize flush 部分数据——下线的设备回的数据本就不全，flush 反而把脏数据落地；
     * 留待设备重新注册后由新的 sync 重新拉完整目录覆盖。</p>
     */
    public void cancel(String deviceId) {
        if (deviceId == null) return;
        CatalogSyncContext removed = contexts.remove(deviceId);
        if (removed == null) return;
        // 持锁改 done：与 accumulate 的 lock block 互斥，保证 in-flight 写入要么跑完要么不跑
        removed.lock.lock();
        try {
            removed.done = true;
        } finally {
            removed.lock.unlock();
        }
        log.info("[通道目录] 取消 in-flight 同步 deviceId={} sn={} total={}/{}",
                deviceId, removed.sn, removed.totalReceived.get(), removed.sumNum);
    }

    /**
     * 启动一次同步：若该设备已有进行中同步且 SN 一致，复用上下文；否则新建。
     *
     * @return 启动（或复用）后的上下文；如果 SN 不一致且已有同步，返回 null（让调用方放弃）
     */
    public CatalogSyncContext startSync(String deviceId, int sn, int sumNum) {
        CatalogSyncContext ctx = contexts.computeIfAbsent(deviceId,
                k -> new CatalogSyncContext(deviceId, sn, sumNum,
                        properties.getCatalog().getFlushThreshold(),
                        properties.getCatalog().getFlushIntervalMs()));
        if (ctx.sn != sn) {
            log.warn("[通道目录] SN 不一致，放弃本次分片 deviceId={} inProgress={} incoming={}",
                    deviceId, ctx.sn, sn);
            return null;
        }
        if (ctx.done) {
            log.info("[通道目录] 同步已完成 deviceId={} sn={} totalReceived={}/{}",
                    deviceId, sn, ctx.totalReceived.get(), ctx.sumNum);
            return null;
        }
        return ctx;
    }

    /**
     * 累加一片 Catalog Item。通道与目录节点两个列表分别追加，都计入累计数；缓冲达到阈值时自动 flush。
     * <p>调用方拿到返回值如果是 true，说明本次 accumulate 触发了"收齐或超时收尾"——handler 应走
     * finalize 路径决定是否删"已不存在通道"。</p>
     */
    public boolean accumulate(CatalogSyncContext ctx,
                              List<GbDeviceChannel> chunkChannels,
                              List<GbGroup> chunkGroups,
                              Map<String, String> channelParentIdMap) {
        if (ctx == null) return false;
        int addedChannels = 0;
        int addedGroups = 0;
        ctx.lock.lock();
        try {
            // 已被 cancel 标记 done：直接放行（不写缓冲、不触发 flush、不再 sumNum 收齐判定）
            if (ctx.done) {
                return false;
            }
            long now = System.currentTimeMillis();
            // 通道去重（重传丢）
            if (chunkChannels != null && !chunkChannels.isEmpty()) {
                for (GbDeviceChannel ch : chunkChannels) {
                    if (ch == null || ch.getChannelId() == null) continue;
                    if (ctx.syncedChannelIds.add(ch.getChannelId())) {
                        ctx.channelBuffer.add(ch);
                        addedChannels++;
                    } else {
                        ctx.duplicateCount.incrementAndGet();
                    }
                }
            }
            if (chunkGroups != null && !chunkGroups.isEmpty()) {
                for (GbGroup g : chunkGroups) {
                    if (g == null || g.getGroupId() == null) continue;
                    if (ctx.syncedGroupIds.add(g.getGroupId())) {
                        ctx.groupBuffer.add(g);
                        addedGroups++;
                    } else {
                        ctx.duplicateCount.incrementAndGet();
                    }
                }
            }
            if (channelParentIdMap != null) {
                ctx.channelParentIdMap.putAll(channelParentIdMap);
            }
            // group 的 parent_id 已在 parseGroup 阶段写入 group 对象本身，map 维护已无必要
            if (addedChannels + addedGroups > 0) {
                ctx.totalReceived.addAndGet(addedChannels + addedGroups);
                ctx.lastNonEmptyUpdateMs.set(now);
            }
            ctx.lastUpdateMs.set(now);

            // INFO 节流：每累计 flushThreshold 条 或 距上次 INFO 满 flushIntervalMs 才打一次。
            // 第一次必打（让日志能看到"同步已开跑"）。复用 flush 阈值/间隔，不引入新配置。
            long total = ctx.totalReceived.get();
            long lastTotal = ctx.lastLogTotalReceived.get();
            long lastMs = ctx.lastLogMs.get();
            boolean firstLog = (lastTotal == 0 && total > 0);
            boolean countHit = (total - lastTotal) >= ctx.flushThreshold;
            boolean timeHit = ctx.flushIntervalMs > 0 && (now - lastMs) >= ctx.flushIntervalMs;
            if (firstLog || countHit || timeHit) {
                log.info("[通道目录] 分片累积 deviceId={} sn={} +{} → total={}/{} buffered={}",
                        ctx.deviceId, ctx.sn, total - lastTotal,
                        total, ctx.sumNum,
                        ctx.channelBuffer.size() + ctx.groupBuffer.size());
                ctx.lastLogTotalReceived.set(total);
                ctx.lastLogMs.set(now);
            }

            // 阈值触发 → flush
            int buffered = ctx.channelBuffer.size() + ctx.groupBuffer.size();
            if (buffered >= ctx.flushThreshold) {
                submitFlush(ctx, false);
            } else if (ctx.flushIntervalMs > 0
                    && now - ctx.lastFlushMs.get() >= ctx.flushIntervalMs
                    && buffered > 0) {
                // 时间触发 → flush（解决设备发包慢时进度条长期不动）
                submitFlush(ctx, false);
            }
        } finally {
            ctx.lock.unlock();
        }
        // 收齐判定：累计 ≥ sumNum → 提交 final flush（不阻塞调用线程）
        if (ctx.sumNum > 0 && ctx.totalReceived.get() >= ctx.sumNum) {
            finalize(ctx, true, "sumNum-收齐");
            return true;
        }
        return false;
    }

    /**
     * 收尾：先 flush 残片，然后根据 completenessThreshold 决定是否清空设备目录。
     * 收尾路径：
     * <ul>
     *   <li>{@code normal=true}：累计达 sumNum，正常收尾</li>
     *   <li>{@code normal=false}：超时/被新同步覆盖等，partial 收尾</li>
     * </ul>
     */
    public void finalize(CatalogSyncContext ctx, boolean normal, String reason) {
        if (ctx == null || ctx.done) return;
        // 1) 锁内改 done / 算 ratio / 提交 final flush
        ctx.lock.lock();
        try {
            if (ctx.done) return;
            ctx.done = true;
            ctx.partial = !normal;
            ctx.finalizeReason = reason;
            submitFlush(ctx, true);
            double ratio = ctx.sumNum > 0 ? (double) ctx.totalReceived.get() / ctx.sumNum : 0.0;
            ctx.completenessRatio = ratio;
        } finally {
            ctx.lock.unlock();
        }
        // 2) 锁外发事件 + 调度清理（避免长锁 + 外部 IO 阻塞）
        try {
            eventPublisher.publishEvent(ctx.buildProgressEvent());
        } catch (Exception e) {
            log.warn("[通道目录] 发布收尾事件失败 deviceId={} err={}", ctx.deviceId, e.getMessage());
        }
        scheduleContextEviction(ctx.deviceId, ctx, 30_000L);
        log.info("[通道目录] 同步收尾 deviceId={} sn={} total={}/{} ratio={} partial={} reason={}",
                ctx.deviceId, ctx.sn, ctx.totalReceived.get(), ctx.sumNum,
                String.format("%.2f", ctx.completenessRatio), ctx.partial, reason);
    }

    /** 提交一次 flush 任务到 catalogSyncExecutor。 */
    private void submitFlush(CatalogSyncContext ctx, boolean isFinalFlush) {
        // 在锁内把当前缓冲快照拷出并清空，避免反复加锁传给 support
        List<GbDeviceChannel> chSnap;
        List<GbGroup> gSnap;
        if (ctx.channelBuffer.isEmpty() && ctx.groupBuffer.isEmpty()) {
            chSnap = List.of();
            gSnap = List.of();
        } else {
            chSnap = new java.util.ArrayList<>(ctx.channelBuffer);
            gSnap = new java.util.ArrayList<>(ctx.groupBuffer);
            ctx.channelBuffer.clear();
            ctx.groupBuffer.clear();
        }
        // channel 的 ParentID 映射只在 final flush 阶段使用（用于通道的 group_id 解析）——
        // 全程累积，不在中间 flush 清空，否则 final flush 拿不到。
        // group 的 parent_id 已在 parseGroup 写入 group 对象本身，不再走独立 map。
        Map<String, String> chParentSnap = isFinalFlush
                ? new java.util.HashMap<>(ctx.channelParentIdMap) : null;
        syncSupport.executeFlush(ctx, chSnap, gSnap, chParentSnap, isFinalFlush);
        // 刷新 lastFlushMs：让时间触发从本次 flush 重新计时
        ctx.lastFlushMs.set(System.currentTimeMillis());
    }

    private void scheduleContextEviction(String deviceId, CatalogSyncContext ctx, long delayMs) {
        // 简易延迟清理：catalogSyncExecutor 自带定时能力有限，这里直接用 catalogSyncExecutor
        // 的 schedule 走 ScheduledExecutorService 派生。这里复用 BizExecutor 不太合适，
        // 简化做法：起一个单线程 ScheduledExecutor。
        EvictionScheduler.schedule(() -> {
            contexts.compute(deviceId, (k, v) -> v == ctx ? null : v);
            log.debug("[通道目录] 上下文已清理 deviceId={}", deviceId);
        }, delayMs);
    }

    /**
     * 30s 周期调：清理完全没响应的僵死上下文（兜底防止内存泄漏）。
     * 直接调 finalize，让其走 final flush + 释放锁 + 发事件。
     */
    public void sweep() {
        long now = System.currentTimeMillis();
        long expireMs = properties.getCatalog().getExpireMs();
        for (CatalogSyncContext ctx : contexts.values()) {
            if (ctx.done) continue;
            if (now - ctx.lastUpdateMs.get() > expireMs) {
                log.warn("[通道目录] batch expire 强收 deviceId={} sn={} total={}/{}",
                        ctx.deviceId, ctx.sn, ctx.totalReceived.get(), ctx.sumNum);
                // 直接 finalize：trigger final flush + 释放锁 + 发事件；handler 那边
                // drainForcedFinalize() 现在是空路径，保留是为了向后兼容。
                finalize(ctx, false, "expire-sweep");
            }
        }
    }

    /**
     * 取走 sweep 强制收尾的上下文（已废：sweep 路径直接调 finalize；保留接口避免破坏编译期契约）。
     * @deprecated 改由 sweep 自己 finalize
     */
    @Deprecated
    public List<CatalogSyncContext> drainForcedFinalize() {
        return List.of();
    }

    public CatalogSyncProgress getProgress(String deviceId) {
        CatalogSyncContext ctx = contexts.get(deviceId);
        if (ctx == null) {
            return CatalogSyncProgress.idle(deviceId);
        }
        return ctx.toProgress();
    }

    /**
     * 内部单线程定时器，单纯用于 30s 延迟清理，无外部注入。
     */
    private static final class EvictionScheduler {
        private static final java.util.concurrent.ScheduledExecutorService EXECUTOR =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "gb-catalog-evict");
                    t.setDaemon(true);
                    return t;
                });

        static void schedule(Runnable r, long delayMs) {
            try {
                EXECUTOR.schedule(r, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("[通道目录] 延迟清理调度失败", e);
            }
        }
    }
}
