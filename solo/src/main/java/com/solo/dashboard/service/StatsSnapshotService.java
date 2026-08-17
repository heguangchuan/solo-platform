package com.solo.dashboard.service;

import com.solo.dashboard.response.DashboardStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 首页 24h 趋势快照服务
 *
 * <h3>存储</h3>
 * <p>用 Redis List 当环形桶，每小时一次 {@code LPUSH} 写入当前数值，{@code LTRIM 0 23}
 * 只留 24 条；TTL = 26h 自动过期，新部署的实例 24h 内会"补齐"历史。</p>
 *
 * <pre>
 * LPUSH dashboard:trend:device_online "14:00,102"
 * LTRIM dashboard:trend:device_online 0 23
 * EXPIRE dashboard:trend:device_online 93600
 * </pre>
 *
 * <h3>读取</h3>
 * <p>{@code LRANGE 0 -1} 拿到的是 [newest, ..., oldest]，需反转得到时序顺序。
 * 空桶（冷启动）返回空列表，前端按"无数据"渲染。</p>
 *
 * <h3>为什么不上 MySQL</h3>
 * <p>24h × N 指标 的历史快照写入压力不大，但读取在首页每次刷新都来一次，
 * 走 Redis 比走 DB 便宜得多；TTL 自动清理免维护；新部署冷启动也无需考虑数据迁移。
 * 真要长期留存再考虑持久化到 MySQL 的 stats_xxx 表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsSnapshotService {

    private static final String DEVICE_KEY = "dashboard:trend:device_online";
    private static final String STREAM_KEY = "dashboard:trend:active_stream";
    private static final int MAX_BUCKETS = 24;
    private static final Duration TTL = Duration.ofHours(26);
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("HH:00");

    private final StringRedisTemplate redis;

    /**
     * 写入一次快照：当前小时 + 数值。
     * 调度入口（{@code DashboardScheduledTasks}）每 1h 调一次。
     *
     * @param onlineDevices 当前在线设备数
     * @param activeStreams 当前活跃流数
     */
    public void snapshot(long onlineDevices, long activeStreams) {
        String hour = LocalDateTime.now().format(HOUR_FMT);
        try {
            push(DEVICE_KEY, hour, onlineDevices);
            push(STREAM_KEY, hour, activeStreams);
        } catch (Exception e) {
            log.warn("[仪表盘] 周期快照异常 hour={} err={}", hour, e.getMessage());
        }
    }

    private void push(String key, String hour, long value) {
        redis.opsForList().leftPush(key, hour + "," + value);
        redis.opsForList().trim(key, 0, MAX_BUCKETS - 1);
        redis.expire(key, TTL);
    }

    /**
     * 读 24h 设备在线趋势
     */
    public List<DashboardStatsResponse.TrendPoint> getDeviceOnlineTrend() {
        return readTrend(DEVICE_KEY);
    }

    /**
     * 读 24h 活跃流趋势
     */
    public List<DashboardStatsResponse.TrendPoint> getActiveStreamTrend() {
        return readTrend(STREAM_KEY);
    }

    /**
     * 取 1h 前的设备在线数（用于 KPI 卡"较 1h 前"差值）
     *
     * @return 1h 前的快照值；null=桶里没数据（冷启动或调度刚跑过 1 次）
     */
    public Long getDeviceOnlineOneHourAgo() {
        return readSecondLatest(DEVICE_KEY);
    }

    public Long getActiveStreamOneHourAgo() {
        return readSecondLatest(STREAM_KEY);
    }

    private List<DashboardStatsResponse.TrendPoint> readTrend(String key) {
        List<String> raw;
        try {
            raw = redis.opsForList().range(key, 0, -1);
        } catch (Exception e) {
            log.warn("[仪表盘] 读趋势失败 key={} err={}", key, e.getMessage());
            return List.of();
        }
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        // LPUSH 后 LRANGE 是 [newest, ..., oldest]，反转得到 [oldest, ..., newest]
        List<String> ordered = new ArrayList<>(raw);
        Collections.reverse(ordered);
        List<DashboardStatsResponse.TrendPoint> out = new ArrayList<>(ordered.size());
        for (String s : ordered) {
            DashboardStatsResponse.TrendPoint p = parse(s);
            if (p != null) out.add(p);
        }
        return out;
    }

    /**
     * 取最新之前的第二条（即 1h 前的值）。
     * 列表 index 0 = newest，index 1 = 上一个 bucket。
     */
    private Long readSecondLatest(String key) {
        try {
            List<String> top2 = redis.opsForList().range(key, 0, 1);
            if (top2 == null || top2.size() < 2) {
                return null;
            }
            DashboardStatsResponse.TrendPoint p = parse(top2.get(1));
            return p == null ? null : p.getValue();
        } catch (Exception e) {
            log.warn("[仪表盘] 读 1h 前快照失败 key={} err={}", key, e.getMessage());
            return null;
        }
    }

    private static DashboardStatsResponse.TrendPoint parse(String s) {
        if (s == null) return null;
        int comma = s.indexOf(',');
        if (comma <= 0 || comma == s.length() - 1) return null;
        try {
            DashboardStatsResponse.TrendPoint p = new DashboardStatsResponse.TrendPoint();
            p.setHour(s.substring(0, comma));
            p.setValue(Long.parseLong(s.substring(comma + 1)));
            return p;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
