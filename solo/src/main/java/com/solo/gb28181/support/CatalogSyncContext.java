package com.solo.gb28181.support;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.event.CatalogSyncProgressEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 *   <li>{@link #lock} 保护缓冲（{@code channelBuffer} / {@code groupBuffer}）的拷贝+清空
 *       复合操作，与 mediaserver 那种"Collections.synchronizedList + ArrayList copy" 的非原子
 *       缺陷解耦——这里所有缓冲写都走 lock。</li>
 *   <li>累计计数 {@link #totalReceived} 用 {@link AtomicLong} 累加，独立于缓冲清空，进度上报不丢。</li>
 *   <li>收尾标志 {@link #done} 一旦置位，{@code accumulate} 后续直接放行（无操作），避免重入。</li>
 * </ul>
 */
public class CatalogSyncContext {

    public final String deviceId;
    public final int sn;
    public final int sumNum;
    public final int flushThreshold;

    /** 滚动缓冲——本次同步累积的 channel，下次 flush 时清空 */
    public final List<GbDeviceChannel> channelBuffer = new ArrayList<>();
    /** 滚动缓冲——本次同步累积的 group */
    public final List<GbGroup> groupBuffer = new ArrayList<>();

    /** 通道 Item 的 <ParentID> 映射（channelId → parentId GB码），仅在 final flush 阶段用 */
    public final Map<String, String> channelParentIdMap = new HashMap<>();
    /** 目录节点 Item 的 <ParentID> 映射（groupId → parentId GB码），仅在 final flush 阶段用 */
    public final Map<String, String> groupParentIdMap = new HashMap<>();

    /** 本次同步所有见过的 channelId（去重 Set，重传丢） */
    public final Set<String> syncedChannelIds = new HashSet<>();
    /** 本次同步所有见过的 groupId（去重 Set） */
    public final Set<String> syncedGroupIds = new HashSet<>();

    /** 累计收到（不清零，与缓冲大小无关），用来判定 sumNum 收齐与暴露进度 */
    public final AtomicLong totalReceived = new AtomicLong(0);
    /** 重传被丢弃的条数（仅用于日志统计） */
    public final AtomicLong duplicateCount = new AtomicLong(0);

    /** 任何 update（包含空响应）的最后时刻——sweep 用 */
    public final AtomicLong lastUpdateMs = new AtomicLong(System.currentTimeMillis());
    /** 仅在有 item 进入时刷——保留旧语义，兼容可能的"分片停顿"判定 */
    public final AtomicLong lastNonEmptyUpdateMs = new AtomicLong(System.currentTimeMillis());

    public final ReentrantLock lock = new ReentrantLock();

    public volatile boolean done = false;
    public volatile boolean partial = false;
    public volatile String finalizeReason;
    /** 收尾时计算：totalReceived / sumNum，决定"是否删已不存在通道" */
    public volatile double completenessRatio = 0.0;

    /** 同步开始时刻（毫秒），由 startSync 时设置 */
    public final long startedAtMs = System.currentTimeMillis();

    public CatalogSyncContext(String deviceId, int sn, int sumNum, int flushThreshold) {
        this.deviceId = deviceId;
        this.sn = sn;
        this.sumNum = sumNum;
        this.flushThreshold = Math.max(1, flushThreshold);
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
