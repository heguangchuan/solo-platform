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

/**
 * <p>提供三件事：</p>
 * <ol>
 *   <li><b>同步锁</b>：{@code gb28181:sync:lock:{deviceId}} SETNX 短锁（5 分钟 TTL），
 *       防止同一设备短时间内被多次触发同步</li>
 *   <li><b>进度状态</b>：{@code gb28181:sync:status:{deviceId}} JSON 字符串（30 分钟 TTL），
 *       记录当前同步的 received / total / percent / status / errorMsg，
 *       给 {@code GET /sync-status} 接口和 WebSocket 推送使用</li>
 *   <li><b>事件订阅</b>：{@link CatalogSyncProgressEvent} 由 {@code CatalogDataCache} 发布，
 *       本类统一落 Redis + 通知释放锁</li>
 * </ol>
 *
 * <h3>为什么用 Redis 而不是 MySQL</h3>
 * <p>进度字段高频写（每片至少一次），不该污染 {@code gb_device} 业务表；30 分钟 TTL 自动回收，
 * 不需要后台清理；跨实例可见（多副本部署时前端轮询任意节点都拿到一致视图）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogSyncStatusService {

    private static final String LOCK_KEY_PREFIX = "gb28181:sync:lock:";
    private static final String STATUS_KEY_PREFIX = "gb28181:sync:status:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration STATUS_TTL = Duration.ofMinutes(30);

    /**
     * 无响应超时：触发同步后 90s 内一片 Catalog 都没收到 → 判 FAILED。
     * <p>设备正常几秒内就该回第一片，90s 已很宽松。固定值，不做配置——只有"完全不应答"
     * 才走这个短超时；"收过分片后中途断流"仍用 {@code catalog.expireMs}（默认 600s）长兜底。</p>
     */
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

    /**
     * 尝试占用同步锁。返回 true 表示抢到锁可发起同步；false 表示已有同步在进行。
     * <p>锁 5 分钟 TTL 自动过期，覆盖设备断流后锁永远不释放的极端情况。</p>
     */
    public boolean tryAcquire(String deviceId) {
        String key = LOCK_KEY_PREFIX + deviceId;
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", LOCK_TTL);
        return Boolean.TRUE.equals(ok);
    }

    /** 显式释放锁（同步完成 / 失败 / 主动取消时调）。 */
    public void release(String deviceId) {
        try {
            redis.delete(LOCK_KEY_PREFIX + deviceId);
        } catch (Exception e) {
            log.warn("[通道目录] 释放同步锁失败 deviceId={} err={}", deviceId, e.getMessage());
        }
    }

    /** 主动取消：清掉状态和锁（inspector 取消同步按钮调用）。 */
    public void cancel(String deviceId) {
        redis.delete(STATUS_KEY_PREFIX + deviceId);
        release(deviceId);
    }

    /** 拉取当前同步状态。返回 null 表示从未同步过。 */
    public String getRawStatus(String deviceId) {
        try {
            return redis.opsForValue().get(STATUS_KEY_PREFIX + deviceId);
        } catch (Exception e) {
            log.warn("[通道目录] 读同步状态失败 deviceId={} err={}", deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 入口：用户调 /sync 接口时 controller 调本方法触发一次同步。
     * <ul>
     *   <li>锁失败 → 抛 "ALREADY_RUNNING"</li>
     *   <li>锁成功 → Redis 写 status=RUNNING，submittedAt=now，发 SIP QueryCatalog</li>
     * </ul>
     */
    public void trigger(String deviceId) {
        if (!tryAcquire(deviceId)) {
            throw new IllegalStateException("ALREADY_RUNNING");
        }
        // 状态初始值：RUNNING 0/0
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
        // 发 SIP Query
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

    /**
     * 订阅 CatalogSyncProgressEvent，写 Redis + 收尾时释放锁。
     * 异步执行（默认 Spring 同步监听器在调用线程跑，对本类无 IO 压力即可）。
     */
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
            // 收尾 → 释放锁
            release(p.getDeviceId());
        }
    }

    /**
     * 超时兜底：扫描所有同步状态，把停留在 RUNNING 且在 {@code catalog.expireMs} 内
     * 一直没有任何进度更新的同步强制收尾为 FAILED。
     *
     * <p>覆盖两类 {@code CatalogDataCache.sweep()} 兜不到的僵尸态：</p>
     * <ul>
     *   <li>设备对 QueryCatalog <b>完全不回任何报文</b>：内存里从未建过 ctx，分片 sweep 扫不到，
     *       Redis 状态会一直停在初始 RUNNING。</li>
     *   <li>回过几片后设备断流，但 ctx 已被提前移除等异常情况。</li>
     * </ul>
     * <p>判据用 {@code lastUpdateAtMs}（初始触发时写入，之后每片刷新），比单纯按 startedAt
     * 更准：只要设备还在持续上报片就不会被误杀。{@code RUNNING} 的进度事件同样会刷新 TTL。</p>
     *
     * @return 本次强制收尾的设备数
     */
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

    /**
     * 把僵尸 RUNNING 状态改写成 FAILED：解析出已有进度字段，用 {@link #toJson} 重建，
     * 保留 received/total/percent 等现场信息，仅把终态置为 FAILED。
     *
     * @param noResponse true=设备从未回片；false=收到分片后中途断流
     */
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

    /**
     * 手动最小化 JSON 序列化（避免引入 ObjectMapper 依赖到本类，结构稳定无需 Jackson）。
     */
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
