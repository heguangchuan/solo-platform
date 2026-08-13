package com.solo.gb28181.support;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.event.CatalogSyncProgressEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 一次目录同步的内存上下文（滚动缓冲 + 累计 + 重传去重 + 收尾标志）。
 * <p>由 {@link com.solo.gb28181.processor.request.message.response.CatalogDataCache}
 * 创建并维护，调用方拿到后调 {@code accumulate()} 即可。</p>
 *
 * <h3>线程模型</h3>
 * <ul>
 *   <li>{@link #lock} 保护缓冲（{@code itemBuffer}）的拷贝+清空复合操作，
 *       用 lock 把这两步做成原子——比 {@code Collections.synchronizedList + ArrayList copy}
 *       的非原子拼接稳，避免"读快照后又来一批被丢掉"这类竞态。</li>
 *   <li>累计计数 {@link #totalReceived} 用 {@link AtomicLong} 累加，独立于缓冲清空，进度上报不丢。</li>
 *   <li>收尾标志 {@link #done} 一旦置位，{@code accumulate} 后续直接放行（无操作），避免重入。</li>
 * </ul>
 *
 * <p>ADR-023：分组（parental=1）与通道（parental=0）合并到同一张 {@code gb_device_channel} 表，
 * 本类只维护一个统一的 item buffer，不再分组/通道分开。</p>
 */
public class CatalogSyncContext {

    public final String deviceId;
    public final int sn;
    public final int sumNum;
    public final int flushThreshold;
    /** 时间触发 flush 间隔（毫秒）：距上次 flush 超过此值且缓冲非空，强制 flush */
    public final long flushIntervalMs;

    /** 滚动缓冲——本次同步累积的所有 catalog item（分组 parental=1 / 通道 parental=0），下次 flush 时清空 */
    public final List<GbDeviceChannel> itemBuffer = new ArrayList<>();

    /**
     * 本次同步所有见过的 {@code channel_id}（20 位国标码，去重 Set，重传丢）。
     * <p>分组与通道都用 channel_id 去重：分组全局唯一由应用层在 saveOrUpdateBatch 内兜底（无 DB 唯一键），
     * 通道在 uk_device_channel 设备内唯一，二者在 device 上下文中都不会重复出现。</p>
     */
    public final Set<String> syncedItemIds = new HashSet<>();

    /** 累计收到（不清零，与缓冲大小无关），用来判定 sumNum 收齐与暴露进度 */
    public final AtomicLong totalReceived = new AtomicLong(0);
    /** 重传被丢弃的条数（仅用于日志统计） */
    public final AtomicLong duplicateCount = new AtomicLong(0);

    /** 任何 update（包含空响应）的最后时刻——sweep 用 */
    public final AtomicLong lastUpdateMs = new AtomicLong(System.currentTimeMillis());
    /** 仅在有 item 进入时刷——保留旧语义，兼容可能的"分片停顿"判定 */
    public final AtomicLong lastNonEmptyUpdateMs = new AtomicLong(System.currentTimeMillis());
    /** 最近一次 flush 的时刻（毫秒）——时间触发 flush 间隔判断用 */
    public final AtomicLong lastFlushMs = new AtomicLong(System.currentTimeMillis());
    /** 上次打 INFO 时的累计值——INFO 节流"每 N 条打一次"判定用，复用 {@link #flushThreshold} */
    public final AtomicLong lastLogTotalReceived = new AtomicLong(0);
    /** 上次打 INFO 时的毫秒时间戳——INFO 节流"每 N 毫秒打一次"判定用，复用 {@link #flushIntervalMs} */
    public final AtomicLong lastLogMs = new AtomicLong(System.currentTimeMillis());

    public final ReentrantLock lock = new ReentrantLock();

    public volatile boolean done = false;
    public volatile boolean partial = false;
    public volatile String finalizeReason;
    /** 收尾时计算：totalReceived / sumNum，决定"是否删已不存在通道" */
    public volatile double completenessRatio = 0.0;

    /** 同步开始时刻（毫秒），由 startSync 时设置 */
    public final long startedAtMs = System.currentTimeMillis();

    public CatalogSyncContext(String deviceId, int sn, int sumNum, int flushThreshold, long flushIntervalMs) {
        this.deviceId = deviceId;
        this.sn = sn;
        this.sumNum = sumNum;
        this.flushThreshold = Math.max(1, flushThreshold);
        this.flushIntervalMs = Math.max(0L, flushIntervalMs);
    }

    public CatalogSyncProgress toProgress() {
        long total = totalReceived.get();
        int sum = sumNum;
        double percent = sum > 0 ? Math.min(100.0, (total * 100.0) / sum) : 0.0;
        return new CatalogSyncProgress(
                deviceId,
                done ? (partial ? "FAILED" : "SUCCESS") : "RUNNING",
                (int) Math.min(total, Integer.MAX_VALUE),
                sum,
                percent,
                startedAtMs,
                System.currentTimeMillis(),
                done ? new java.sql.Timestamp(System.currentTimeMillis()).toString() : null,
                partial ? (finalizeReason != null ? finalizeReason : "partial") : null,
                duplicateCount.intValue()
        );
    }

    public CatalogSyncProgressEvent buildProgressEvent() {
        return new CatalogSyncProgressEvent(toProgress());
    }
}
