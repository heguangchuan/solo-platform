package com.solo.gb28181.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.event.CatalogSyncProgressEvent;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.transmit.cmd.SipCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Catalog 同步状态（Redis 持久 + 进度订阅）。
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

    private final StringRedisTemplate redis;
    private final SipCommander sipCommander;
    private final GbDeviceMapper deviceMapper;

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

    /** 主动取消：清掉状态和锁（仅在"前端希望立即停止同步"时调，目前未暴露入口） */
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
