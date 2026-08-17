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

/**
 * 上级平台 RecordInfo 查询映射表（ADR-031 落地）。
 *
 * <p>本平台作为下级挂上级时，上级发来 {@code Query/RecordInfo}，本平台要主动向下级设备
 * 转发同样格式的 {@code Query/RecordInfo}。但响应回来时携带的是本平台生成的 inner SN，
 * 上级认的是 outer SN——所以需要这个映射表把 inner SN → (platform, outer SN, channelId) 记录下来，
 * 设备响应到达时按映射改 outer SN 转回给上级。</p>
 *
 * <p><b>生命周期：</b>30 秒超时清理由内部单线程定时器扫描；超时条目会被主动给上级
 * 回 {@code SumNum=0} 的空响应（避免上级无限等）。和 {@link com.solo.gb28181.processor.request.message.response.RecordInfoDataCache}
 * 的 30s 过期语义对齐，行为一致。</p>
 *
 * <p><b>与现有播放查询的关系：</b>内部 {@code GbPlaybackService} 查设备用的是自己的 SN，
 * 不注册到这里，所以不会和上级查询的路由冲突——response handler 命中不到本表的 SN
 * 就走现有 {@code playbackService.onRecordInfoReceived} 路径。</p>
 *
 * <p><b>未实现：</b>子平台（{@code GbPlatform}）作为中转的多级级联转发——上级的
 * {@code DeviceID} 命中子平台时要再向下级联一跳，那是后续迭代。</p>
 */
@Slf4j
@Component
public class UpperRecordQueryHolder {

    /** 与 RecordInfoDataCache.EXPIRE_MS 对齐：30s 内设备没回完就强收 */
    private static final long EXPIRE_MS = 30_000L;

    /** 单线程定时器：每 5s 扫一次过期条目 */
    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private final PlatformCommander platformCommander;
    private final Map<Integer, Entry> pending = new ConcurrentHashMap<>();

    public UpperRecordQueryHolder(PlatformCommander platformCommander) {
        this.platformCommander = platformCommander;
        EvictionScheduler.scheduleAtFixedRate(this::sweep, SWEEP_INTERVAL_MS, SWEEP_INTERVAL_MS);
    }

    /**
     * 注册一次上级查询：本平台向下级设备发 {@code Query/RecordInfo} 之前调。
     *
     * @param snInner 本平台生成的 SN（传给设备用）
     * @param ctx     上级侧上下文（路由 + outer SN + 通道 ID）
     */
    public void register(int snInner, UpperQueryContext ctx) {
        pending.put(snInner, new Entry(ctx, System.currentTimeMillis()));
    }

    /**
     * 设备响应到达时调：命中则取并移除（一次性消费）；未命中表示这是内部播放查询的响应。
     */
    public UpperQueryContext consume(int snInner) {
        Entry e = pending.remove(snInner);
        return e == null ? null : e.context;
    }

    /**
     * 主动丢弃（设备在本平台查不到、handler 立即给上级回空时调）。
     */
    public void cancel(int snInner) {
        pending.remove(snInner);
    }

    /** 扫过期条目：每条主动给上级回 SumNum=0 空响应，避免上级无限等 */
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

    /**
     * 上级侧查询上下文（路由信息）
     *
     * @param platform 上级平台配置
     * @param snOuter  上级请求的 SN（响应时透传回去）
     * @param deviceId 上级请求的 DeviceID（响应时透传回去，按 GB 协议此处通常是 channelId）
     */
    public record UpperQueryContext(GbParentPlatform platform, String snOuter, String deviceId) {}

    private record Entry(UpperQueryContext context, long registerMs) {}

    /** 单线程定时器：daemon，不阻止 JVM 退出 */
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
