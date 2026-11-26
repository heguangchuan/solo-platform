package com.solo.gb28181.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.event.catalog.CatalogSyncProgressEvent;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.transmit.cmd.SipCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 每片都更新进度，放 Redis 比写设备表合适，多实例也都能读到。
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogSyncStatusService {

    private static final String LOCK_KEY_PREFIX = "gb28181:sync:lock:";
    private static final String STATUS_KEY_PREFIX = "gb28181:sync:status:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration STATUS_TTL = Duration.ofMinutes(30);

    // 一片都没回用 90s 短超时；收到过再断流才走 catalog.expireMs 的长超时。
    private static final long NO_RESPONSE_MS = 90_000L;

    private final StringRedisTemplate redis;
    private final SipCommander sipCommander;
    private final GbDeviceMapper deviceMapper;
    private final Gb28181Properties properties;

    private static final Pattern RUNNING_PATTERN = Pattern.compile("\"status\":\"RUNNING\"");
    private static final Pattern LAST_UPDATE_PATTERN =
            Pattern.compile("\"lastUpdateAtMs\":(\\d+)");
    private static final Pattern STARTED_PATTERN = Pattern.compile("\"startedAtMs\":(\\d+)");
    private static final Pattern RECEIVED_PATTERN = Pattern.compile("\"received\":(\\d+)");
    private static final Pattern TOTAL_PATTERN = Pattern.compile("\"total\":(\\d+)");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("\"percent\":([0-9.]+)");
    private static final Pattern DUPLICATE_PATTERN = Pattern.compile("\"duplicateCount\":(\\d+)");

    public boolean tryAcquire(String deviceId) {
        String key = LOCK_KEY_PREFIX + deviceId;
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", LOCK_TTL);
        return Boolean.TRUE.equals(ok);
    }

    public void release(String deviceId) {
        try {
            redis.delete(LOCK_KEY_PREFIX + deviceId);
        } catch (Exception e) {
            log.warn("[通道目录] 释放同步锁失败 deviceId={} err={}", deviceId, e.getMessage());
        }
    }

    public void cancel(String deviceId) {
        redis.delete(STATUS_KEY_PREFIX + deviceId);
        release(deviceId);
    }

    public String getRawStatus(String deviceId) {
        try {
            return redis.opsForValue().get(STATUS_KEY_PREFIX + deviceId);
        } catch (Exception e) {
            log.warn("[通道目录] 读同步状态失败 deviceId={} err={}", deviceId, e.getMessage());
            return null;
        }
    }

    public void trigger(String deviceId) {
        if (!tryAcquire(deviceId)) {
            throw new IllegalStateException("ALREADY_RUNNING");
        }
        String initialJson = "{\"deviceId\":\"" + deviceId
                + "\",\"status\":\"RUNNING\",\"received\":0,\"total\":0,\"percent\":0.0"
                + ",\"startedAtMs\":" + System.currentTimeMillis()
                + ",\"lastUpdateAtMs\":" + System.currentTimeMillis()
                + ",\"duplicateCount\":0}";
        try {
            redis.opsForValue().set(STATUS_KEY_PREFIX + deviceId, initialJson, STATUS_TTL);
        } catch (Exception e) {
            log.warn("[通道目录] 写初始状态失败 deviceId={} err={}", deviceId, e.getMessage());
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device == null) {
            release(deviceId);
            throw new IllegalArgumentException("DEVICE_NOT_FOUND");
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            release(deviceId);
            throw new IllegalStateException("DEVICE_OFFLINE");
        }
        sipCommander.queryDeviceInfo(device);
        sipCommander.queryCatalog(device);
        log.info("[通道目录] 已触发同步 deviceId={}", deviceId);
    }

    @EventListener
    public void onProgress(CatalogSyncProgressEvent event) {
        CatalogSyncProgress p = event.getProgress();
        if (p == null) {
            return;
        }
        try {
            String json = toJson(p);
            redis.opsForValue().set(STATUS_KEY_PREFIX + p.getDeviceId(), json, STATUS_TTL);
        } catch (Exception e) {
            log.warn("[通道目录] 写同步状态失败 deviceId={} err={}", p.getDeviceId(), e.getMessage());
        }
        if (!CatalogSyncProgress.STATUS_RUNNING.equals(p.getStatus())) {
            release(p.getDeviceId());
        }
    }

    // 设备一片没回时根本不会建 CatalogDataCache 上下文，只能靠这里的 Redis 状态收尾。
    public int sweepRunningTimeouts() {
        long stallMs = properties.getCatalog().getExpireMs();
        long now = System.currentTimeMillis();
        int timedOut = 0;
        ScanOptions options = ScanOptions.scanOptions()
                .match(STATUS_KEY_PREFIX + "*").count(256).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String json = redis.opsForValue().get(key);
                if (json == null || !RUNNING_PATTERN.matcher(json).find()) {
                    continue;
                }
                Matcher m = LAST_UPDATE_PATTERN.matcher(json);
                if (!m.find()) {
                    continue;
                }
                long lastUpdate = Long.parseLong(m.group(1));
                long silentMs = now - lastUpdate;
                // received=0：设备一片都没回，用短超时；收过分片后中途断流才用长超时
                int received = (int) matchLong(RECEIVED_PATTERN, json, 0L);
                long timeoutMs = received == 0 ? NO_RESPONSE_MS : stallMs;
                if (silentMs < timeoutMs) {
                    continue;
                }
                String deviceId = key.substring(STATUS_KEY_PREFIX.length());
                markTimeoutFailed(deviceId, json, received == 0);
                release(deviceId);
                timedOut++;
                log.warn("[通道目录] 同步超时，强制收尾 deviceId={} received={} 静默={}ms 阈值={}ms",
                        deviceId, received, silentMs, timeoutMs);
            }
        } catch (Exception e) {
            log.warn("[通道目录] 扫描超时同步状态失败 err={}", e.getMessage());
        }
        return timedOut;
    }

    private void markTimeoutFailed(String deviceId, String runningJson, boolean noResponse) {
        long startedAt = matchLong(STARTED_PATTERN, runningJson, 0L);
        int received = (int) matchLong(RECEIVED_PATTERN, runningJson, 0L);
        int total = (int) matchLong(TOTAL_PATTERN, runningJson, 0L);
        int duplicate = (int) matchLong(DUPLICATE_PATTERN, runningJson, 0L);
        double percent = matchDouble(PERCENT_PATTERN, runningJson, 0.0);
        String finishedAt = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String errorMsg = noResponse
                ? "设备无目录响应，已超时收尾"
                : "设备目录同步中途长时间无进度，已超时收尾";
        CatalogSyncProgress failed = new CatalogSyncProgress(
                deviceId, CatalogSyncProgress.STATUS_FAILED, received, total, percent,
                startedAt, System.currentTimeMillis(), finishedAt, errorMsg, duplicate);
        try {
            redis.opsForValue().set(STATUS_KEY_PREFIX + deviceId, toJson(failed), STATUS_TTL);
        } catch (Exception e) {
            log.warn("[通道目录] 写超时收尾状态失败 deviceId={} err={}", deviceId, e.getMessage());
        }
    }

    private static long matchLong(Pattern p, String json, long fallback) {
        Matcher m = p.matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : fallback;
    }

    private static double matchDouble(Pattern p, String json, double fallback) {
        Matcher m = p.matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : fallback;
    }

    private String toJson(CatalogSyncProgress p) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
                .append("\"deviceId\":\"").append(esc(p.getDeviceId())).append('"')
                .append(",\"status\":\"").append(p.getStatus()).append('"')
                .append(",\"received\":").append(p.getReceived())
                .append(",\"total\":").append(p.getTotal())
                .append(",\"percent\":").append(String.format("%.2f", p.getPercent()))
                .append(",\"startedAtMs\":").append(p.getStartedAtMs())
                .append(",\"lastUpdateAtMs\":").append(p.getLastUpdateAtMs());
        if (p.getFinishedAt() != null) {
            sb.append(",\"finishedAt\":\"").append(esc(p.getFinishedAt())).append('"');
        }
        if (p.getErrorMsg() != null) {
            sb.append(",\"errorMsg\":\"").append(esc(p.getErrorMsg())).append('"');
        }
        sb.append(",\"duplicateCount\":").append(p.getDuplicateCount())
                .append('}');
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
