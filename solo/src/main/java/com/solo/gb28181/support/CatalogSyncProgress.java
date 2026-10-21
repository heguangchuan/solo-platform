package com.solo.gb28181.support;

import java.time.LocalDateTime;

/**
 * <p>{@link #status} 取值：</p>
 * <ul>
 *   <li>{@code IDLE}：从未同步过 / 上下文已清理</li>
 *   <li>{@code RUNNING}：同步进行中</li>
 *   <li>{@code SUCCESS}：正常收齐（partial=false）</li>
 *   <li>{@code FAILED}：超时 / 异常收尾（partial=true）</li>
 * </ul>
 */
public class CatalogSyncProgress {

    public static final String STATUS_IDLE = "IDLE";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private final String deviceId;
    private final String status;
    private final int received;
    private final int total;
    private final double percent;
    private final long startedAtMs;
    private final long lastUpdateAtMs;
    private final String finishedAt;
    private final String errorMsg;
    private final int duplicateCount;

    public CatalogSyncProgress(String deviceId, String status, int received, int total,
                               double percent, long startedAtMs, long lastUpdateAtMs,
                               String finishedAt, String errorMsg, int duplicateCount) {
        this.deviceId = deviceId;
        this.status = status;
        this.received = received;
        this.total = total;
        this.percent = percent;
        this.startedAtMs = startedAtMs;
        this.lastUpdateAtMs = lastUpdateAtMs;
        this.finishedAt = finishedAt;
        this.errorMsg = errorMsg;
        this.duplicateCount = duplicateCount;
    }

    public static CatalogSyncProgress idle(String deviceId) {
        return new CatalogSyncProgress(deviceId, STATUS_IDLE, 0, 0, 0.0,
                0L, 0L, null, null, 0);
    }

    public String getDeviceId() { return deviceId; }
    public String getStatus() { return status; }
    public int getReceived() { return received; }
    public int getTotal() { return total; }
    public double getPercent() { return percent; }
    public long getStartedAtMs() { return startedAtMs; }
    public long getLastUpdateAtMs() { return lastUpdateAtMs; }
    public String getFinishedAt() { return finishedAt; }
    public String getErrorMsg() { return errorMsg; }
    public int getDuplicateCount() { return duplicateCount; }

    public LocalDateTime getStartedAt() {
        return startedAtMs > 0
                ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startedAtMs), java.time.ZoneId.systemDefault())
                : null;
    }
    public LocalDateTime getLastUpdateAt() {
        return lastUpdateAtMs > 0
                ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastUpdateAtMs), java.time.ZoneId.systemDefault())
                : null;
    }
}
