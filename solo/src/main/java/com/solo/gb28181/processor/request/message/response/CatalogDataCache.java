package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.support.CatalogSyncContext;
import com.solo.gb28181.support.CatalogSyncProgress;
import com.solo.gb28181.support.CatalogSyncSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 别等 sumNum 全收齐再入库，大目录真会把内存撑爆。按条数/时间分批 flush，累计数不随缓冲清空。
// 只有完整度达到阈值的那轮才允许删旧通道。
@Slf4j
@Component
public class CatalogDataCache {

    private final Gb28181Properties properties;
    private final CatalogSyncSupport syncSupport;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, CatalogSyncContext> contexts = new ConcurrentHashMap<>();

    public CatalogDataCache(Gb28181Properties properties,
                            CatalogSyncSupport syncSupport,
                            ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.syncSupport = syncSupport;
        this.eventPublisher = eventPublisher;
    }

    // 取消时先在锁里标 done，已入队的 flush 也会再查一次；别把没收齐的目录再收尾落库。
    public void cancel(String deviceId) {
        if (deviceId == null) {
            return;
        }
        CatalogSyncContext removed = contexts.remove(deviceId);
        if (removed == null) {
            return;
        }
        removed.lock.lock();
        try {
            removed.done = true;
        } finally {
            removed.lock.unlock();
        }
        log.info("[通道目录] 取消 in-flight 同步 deviceId={} sn={} total={}/{}",
                deviceId, removed.sn, removed.totalReceived.get(), removed.sumNum);
    }

    // SN 变了说明设备重新发目录，旧那轮不可能再收齐，直接 partial 收尾让位。
    // 要是拒绝新 SN，只能干等旧上下文超时，新分片还会全被丢掉。
    public CatalogSyncContext startSync(String deviceId, int sn, int sumNum) {
        CatalogSyncContext existing = contexts.get(deviceId);
        if (existing != null && existing.sn != sn) {
            // SN 变了 = 设备重发目录，旧那轮不可能再收齐，partial 收尾让位
            log.info("[通道目录] SN 变更，旧同步 partial 收尾后由新 SN 接管 deviceId={} 旧SN={} 新SN={} 旧进度={}/{}",
                    deviceId, existing.sn, sn, existing.totalReceived.get(), existing.sumNum);
            finalize(existing, false, "SN 变更被新同步抢占");
            contexts.remove(deviceId, existing);
        }
        CatalogSyncContext ctx = contexts.computeIfAbsent(deviceId,
                k -> new CatalogSyncContext(deviceId, sn, sumNum,
                        properties.getCatalog().getFlushThreshold(),
                        properties.getCatalog().getFlushIntervalMs()));
        if (ctx.done) {
            log.info("[通道目录] 同步已完成 deviceId={} sn={} totalReceived={}/{}",
                    deviceId, sn, ctx.totalReceived.get(), ctx.sumNum);
            return null;
        }
        return ctx;
    }

    // 返回 true 表示这片触发了收尾；declaredNum 只记诊断，不参与收齐判断。
    public boolean accumulate(CatalogSyncContext ctx, List<GbDeviceChannel> items, Integer declaredNum) {
        if (ctx == null) {
            return false;
        }
        int added = 0;
        // 中间进度也得发，不然库里都写几万条了，前端还停在 0/0
        boolean publishProgress = false;
        ctx.lock.lock();
        try {
            if (ctx.done) {
                return false;
            }
            long now = System.currentTimeMillis();
            ctx.chunkCount.incrementAndGet();
            if (declaredNum != null) {
                ctx.declaredNumSum.addAndGet(declaredNum);
            }
            if (items != null && !items.isEmpty()) {
                for (GbDeviceChannel it : items) {
                    if (it == null || it.getChannelId() == null) {
                        continue;
                    }
                    if (ctx.syncedItemIds.add(it.getChannelId())) {
                        ctx.itemBuffer.add(it);
                        added++;
                    } else {
                        ctx.duplicateCount.incrementAndGet();
                    }
                }
            }
            if (added > 0) {
                ctx.totalReceived.addAndGet(added);
                ctx.lastNonEmptyUpdateMs.set(now);
            }
            ctx.lastUpdateMs.set(now);

            // 日志和中间进度按条数/时间节流，第一片先来一条
            long total = ctx.totalReceived.get();
            long lastTotal = ctx.lastLogTotalReceived.get();
            long lastMs = ctx.lastLogMs.get();
            boolean firstLog = (lastTotal == 0 && total > 0);
            boolean countHit = (total - lastTotal) >= ctx.flushThreshold;
            boolean timeHit = ctx.flushIntervalMs > 0 && (now - lastMs) >= ctx.flushIntervalMs;
            if (firstLog || countHit || timeHit) {
                log.info("[通道目录] 分片累积 设备:{} SN:{} 新增:{} 总数:{}/{} 已缓冲:{}",
                        ctx.deviceId, ctx.sn, total - lastTotal,
                        total, ctx.sumNum,
                        ctx.itemBuffer.size());
                ctx.lastLogTotalReceived.set(total);
                ctx.lastLogMs.set(now);
                publishProgress = true;
            }

            int buffered = ctx.itemBuffer.size();
            if (buffered >= ctx.flushThreshold) {
                submitFlush(ctx, false);
            } else if (ctx.flushIntervalMs > 0
                    && now - ctx.lastFlushMs.get() >= ctx.flushIntervalMs
                    && buffered > 0) {
                submitFlush(ctx, false);
            }
        } finally {
            ctx.lock.unlock();
        }
        // Redis IO 放锁外，别卡住分片写入
        if (publishProgress) {
            try {
                eventPublisher.publishEvent(ctx.buildProgressEvent());
            } catch (Exception e) {
                log.warn("[通道目录] 推送中间进度失败 deviceId={} err={}", ctx.deviceId, e.getMessage());
            }
        }
        // 收齐判定：累计 ≥ sumNum → 提交 final flush（不阻塞调用线程）
        if (ctx.sumNum > 0 && ctx.totalReceived.get() >= ctx.sumNum) {
            finalize(ctx, true, "sumNum-收齐");
            return true;
        }
        return false;
    }

    public void finalize(CatalogSyncContext ctx, boolean normal, String reason) {
        if (ctx == null || ctx.done) {
            return;
        }
        // 1) 锁内改 done / 算 ratio / 提交 final flush
        ctx.lock.lock();
        try {
            if (ctx.done) {
                return;
            }
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
        log.info("[通道目录] 同步收尾 deviceId={} sn={} 唯一={}/{} ratio={} 分片={} 声明合计={} 重复={} partial={} reason={}",
                ctx.deviceId, ctx.sn, ctx.totalReceived.get(), ctx.sumNum,
                String.format("%.4f", ctx.completenessRatio),
                ctx.chunkCount.get(), ctx.declaredNumSum.get(), ctx.duplicateCount.get(),
                ctx.partial, reason);
    }

    private void submitFlush(CatalogSyncContext ctx, boolean isFinalFlush) {
        // 在锁内把当前缓冲快照拷出并清空，避免反复加锁传给 support
        List<GbDeviceChannel> snap;
        if (ctx.itemBuffer.isEmpty()) {
            snap = List.of();
        } else {
            snap = new java.util.ArrayList<>(ctx.itemBuffer);
            ctx.itemBuffer.clear();
        }
        syncSupport.executeFlush(ctx, snap, isFinalFlush);
        ctx.lastFlushMs.set(System.currentTimeMillis());
    }

    // 单独用 daemon 线程做延迟删除，别占目录入库池。
    private void scheduleContextEviction(String deviceId, CatalogSyncContext ctx, long delayMs) {
        EvictionScheduler.schedule(() -> {
            contexts.compute(deviceId, (k, v) -> v == ctx ? null : v);
            log.debug("[通道目录] 上下文已清理 deviceId={}", deviceId);
        }, delayMs);
    }

    // 完全没新分片太久就强制 partial 收尾。
    public void sweep() {
        long now = System.currentTimeMillis();
        long expireMs = properties.getCatalog().getExpireMs();
        for (CatalogSyncContext ctx : contexts.values()) {
            if (ctx.done) {
                continue;
            }
            if (now - ctx.lastUpdateMs.get() > expireMs) {
                log.warn("[通道目录] batch expire 强收 deviceId={} sn={} total={}/{}",
                        ctx.deviceId, ctx.sn, ctx.totalReceived.get(), ctx.sumNum);
                finalize(ctx, false, "expire-sweep");
            }
        }
    }

    // 老接口，现在 sweep 自己 finalize。
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
