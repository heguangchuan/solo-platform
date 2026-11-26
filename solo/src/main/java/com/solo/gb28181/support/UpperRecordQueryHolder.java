package com.solo.gb28181.support;

import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 设备只认 inner SN，上级只认 outer SN，这张表负责换回来。
// 30s 没收完就消费掉并给上级回空；内部回放查询不进这张表。
@Slf4j
@Component
public class UpperRecordQueryHolder {

    private static final long EXPIRE_MS = 30_000L;

    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private final PlatformCommander platformCommander;
    private final Map<Integer, Entry> pending = new ConcurrentHashMap<>();

    public UpperRecordQueryHolder(PlatformCommander platformCommander) {
        this.platformCommander = platformCommander;
        EvictionScheduler.scheduleAtFixedRate(this::sweep, SWEEP_INTERVAL_MS, SWEEP_INTERVAL_MS);
    }

    public void register(int snInner, UpperQueryContext ctx) {
        pending.put(snInner, new Entry(ctx, System.currentTimeMillis()));
    }

    public UpperQueryContext consume(int snInner) {
        Entry e = pending.remove(snInner);
        return e == null ? null : e.context;
    }

    public void cancel(int snInner) {
        pending.remove(snInner);
    }

    void sweep() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Entry> e : pending.entrySet()) {
            if (now - e.getValue().registerMs <= EXPIRE_MS) {
                continue;
            }
            int snInner = e.getKey();
            // 双重检查：被并发消费掉的不要重复回
            if (pending.remove(snInner) == null) {
                continue;
            }
            UpperQueryContext ctx = e.getValue().context;
            log.warn("[级联录像查询] 设备 {} 设备未在 {}ms 内响应，给上级 {} SN={} 回空响应",
                    ctx.deviceId(), EXPIRE_MS, ctx.platform().getServerGbId(), ctx.snOuter());
            try {
                platformCommander.sendRecordInfoResponse(
                        ctx.platform(), ctx.snOuter(), ctx.deviceId(), List.of());
            } catch (Exception ex) {
                log.warn("[级联录像查询] 超时回空失败 platform={} SN={}: {}",
                        ctx.platform().getServerGbId(), ctx.snOuter(), ex.getMessage());
            }
        }
    }

    public record UpperQueryContext(GbParentPlatform platform, String snOuter, String deviceId) {}

    private record Entry(UpperQueryContext context, long registerMs) {}

    private static final class EvictionScheduler {
        private static final ScheduledExecutorService EXECUTOR =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "gb-upper-record-sweep");
                    t.setDaemon(true);
                    return t;
                });

        static void scheduleAtFixedRate(Runnable r, long initialDelayMs, long periodMs) {
            try {
                EXECUTOR.scheduleAtFixedRate(r, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("[级联录像查询] sweep 调度失败", e);
            }
        }
    }
}
