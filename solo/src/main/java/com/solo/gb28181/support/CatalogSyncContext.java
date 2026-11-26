package com.solo.gb28181.support;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.event.catalog.CatalogSyncProgressEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

// lock 主要护住 itemBuffer 的“拷贝 + 清空”，拆开做会把两份之间新到的数据弄丢。
// done 之后不再收分片；totalReceived 是累计数，不跟缓冲一起清。
public class CatalogSyncContext {

    public final String deviceId;
    public final int sn;
    public final int sumNum;
    public final int flushThreshold;
    public final long flushIntervalMs;

    public final List<GbDeviceChannel> itemBuffer = new ArrayList<>();

    // 分组和通道都按 channel_id 去重。
    public final Set<String> syncedItemIds = new HashSet<>();

    public final AtomicLong totalReceived = new AtomicLong(0);
    public final AtomicLong duplicateCount = new AtomicLong(0);
    public final AtomicLong chunkCount = new AtomicLong(0);
    public final AtomicLong declaredNumSum = new AtomicLong(0);

    // 空分片也刷新，sweep 靠它判断是不是彻底没消息了。
    public final AtomicLong lastUpdateMs = new AtomicLong(System.currentTimeMillis());
    public final AtomicLong lastNonEmptyUpdateMs = new AtomicLong(System.currentTimeMillis());
    public final AtomicLong lastFlushMs = new AtomicLong(System.currentTimeMillis());
    public final AtomicLong lastLogTotalReceived = new AtomicLong(0);
    public final AtomicLong lastLogMs = new AtomicLong(System.currentTimeMillis());

    public final ReentrantLock lock = new ReentrantLock();

    public volatile boolean done = false;
    public volatile boolean partial = false;
    public volatile String finalizeReason;
    // 收尾时算，低于阈值就别删旧通道。
    public volatile double completenessRatio = 0.0;

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
