package com.solo.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.utils.UserHolderUtils;
import com.solo.dashboard.response.DashboardStatsResponse;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.media.entity.MediaServer;
import com.solo.media.entity.StreamProxy;
import com.solo.media.entity.StreamPush;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.mapper.StreamProxyMapper;
import com.solo.media.mapper.StreamPushMapper;
import com.solo.media.service.MediaServerLoader;
import com.solo.system.entity.SysMessage;
import com.solo.system.mapper.SysMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 首页聚合统计服务
 *
 * <h3>职责</h3>
 * <p>一次返回首页渲染所需的全部数据，避免前端 6+ 次瀑布请求。
 * 数据来源分散在 MySQL（设备/通道/节点/消息/用户角色权限）、Redis（ZLM 负载 + 趋势桶）。</p>
 *
 * <h3>性能</h3>
 * <p>所有查询走主键 / 索引（{@code idx_status}、{@code idx_create_time}），单次聚合
 * 大约 6~10 条 SQL + 2~3 次 Redis 调用，预期 50ms 内返回。</p>
 *
 * <h3>健壮性</h3>
 * <p>任意一个数据源失败不影响整体返回——失败的部分降级为空值或 0，
 * 前端按"无数据"渲染。这样 Redis 抖动、sys_message 表为空等边界情况都不会让首页 500。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final GbDeviceMapper deviceMapper;
    private final GbMediaServerMapper mediaServerMapper;
    private final SysMessageMapper messageMapper;
    private final MediaServerLoader mediaServerLoader;
    private final StatsSnapshotService snapshotService;
    private final StreamPushMapper streamPushMapper;
    private final StreamProxyMapper streamProxyMapper;
    private final GbInviteSessionService gbInviteSessionService;

    public DashboardStatsResponse aggregate() {
        DashboardStatsResponse out = new DashboardStatsResponse();
        out.setGreeting(buildGreeting());
        out.setDevice(buildDeviceStats());
        out.setMediaServer(buildMediaServerList());
        out.setStream(buildStreamStats());
        out.setAlert(buildAlert());
        out.setTrends(buildTrends());
        out.setManufacturerDist(buildManufacturerDist());
        return out;
    }

    // ============================================================
    // 各 section 单独构造，单点失败不影响整体
    // ============================================================

    private DashboardStatsResponse.Greeting buildGreeting() {
        DashboardStatsResponse.Greeting g = new DashboardStatsResponse.Greeting();
        try {
            g.setUsername(UserHolderUtils.getCurrentUsername());
        } catch (Exception e) {
            g.setUsername("admin");
        }
        g.setHour(LocalDateTime.now().getHour());
        return g;
    }

    private DashboardStatsResponse.Device buildDeviceStats() {
        DashboardStatsResponse.Device d = new DashboardStatsResponse.Device();
        try {
            long total = deviceMapper.selectCount(null);
            long online = deviceMapper.selectCount(
                    new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getStatus, 1));
            long unregistered = deviceMapper.selectCount(
                    new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getStatus, 2));
            long offline = total - online - unregistered;
            d.setTotal(total);
            d.setOnline(online);
            d.setOffline(Math.max(offline, 0));
            d.setUnregistered(unregistered);
            d.setOnlineRate(total == 0 ? 0d : (double) online / total);
        } catch (Exception e) {
            log.warn("[仪表盘] 设备统计失败 err={}", e.getMessage());
            d.setTotal(0L); d.setOnline(0L); d.setOffline(0L); d.setUnregistered(0L);
            d.setOnlineRate(0d);
        }
        return d;
    }

    private List<DashboardStatsResponse.MediaServer> buildMediaServerList() {
        try {
            List<MediaServer> all = mediaServerMapper.selectList(null);
            // 一次性把三类流的 serverId 分布拉出来，避免 N+1
            Map<String, Long> gbByServer = safeGroupByServerId(
                    () -> gbInviteSessionService.listAllActive(),
                    GbInviteSession::getServerId);
            Map<String, Long> pushByServer = safeGroupByServerId(
                    () -> streamPushMapper.selectList(null),
                    StreamPush::getMediaServerId);
            Map<String, Long> proxyByServer = safeGroupByServerId(
                    () -> streamProxyMapper.selectList(
                            new LambdaQueryWrapper<StreamProxy>().eq(StreamProxy::getStatus, 1)),
                    StreamProxy::getMediaServerId);

            List<DashboardStatsResponse.MediaServer> out = new ArrayList<>(all.size());
            for (MediaServer s : all) {
                DashboardStatsResponse.MediaServer m = new DashboardStatsResponse.MediaServer();
                String sid = s.getServerId();
                m.setServerId(sid);
                m.setName(s.getName());
                m.setStatus(s.getStatus());
                Double load = mediaServerLoader.getLoad(sid);
                m.setLoadScore(load == null ? 0d : load);
                m.setHostIp(s.getHostIp());
                m.setHttpPort(s.getHttpPort());
                m.setGbStream(gbByServer.getOrDefault(sid, 0L));
                m.setPushStream(pushByServer.getOrDefault(sid, 0L));
                m.setProxyStream(proxyByServer.getOrDefault(sid, 0L));
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            log.warn("[仪表盘] ZLM 节点列表失败 err={}", e.getMessage());
            return List.of();
        }
    }

    private DashboardStatsResponse.Stream buildStreamStats() {
        DashboardStatsResponse.Stream s = new DashboardStatsResponse.Stream();
        try {
            // 活跃流 = ZLM Redis ZSet 总 score（每路流就绪 +1、断开 -1），含 GB / 推流 / 拉流 全部
            s.setLive(mediaServerLoader.getTotalLoad());
        } catch (Exception e) {
            log.warn("[仪表盘] 活跃流统计失败 err={}", e.getMessage());
            s.setLive(0L);
        }
        // 推流：stream_push 表里每行代表一路活跃推流（on_stream_changed 驱动物理删/增）
        try {
            s.setPush(streamPushMapper.selectCount(null));
        } catch (Exception e) {
            log.warn("[仪表盘] 推流统计失败 err={}", e.getMessage());
            s.setPush(0L);
        }
        // 拉流代理：status=1 即 ZLM 上正在跑的代理
        try {
            s.setProxy(streamProxyMapper.selectCount(
                    new LambdaQueryWrapper<StreamProxy>().eq(StreamProxy::getStatus, 1)));
        } catch (Exception e) {
            log.warn("[仪表盘] 拉流代理统计失败 err={}", e.getMessage());
            s.setProxy(0L);
        }
        return s;
    }

    private DashboardStatsResponse.Alert buildAlert() {
        DashboardStatsResponse.Alert a = new DashboardStatsResponse.Alert();
        try {
            String username = UserHolderUtils.getCurrentUsername();
            Long unread = messageMapper.selectCount(
                    new LambdaQueryWrapper<SysMessage>()
                            .eq(SysMessage::getUsername, username)
                            .eq(SysMessage::getReadFlag, 0));
            a.setUnreadCount(unread == null ? 0L : unread);

            // 当天消息数（按 create_time 过滤）
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            Long today = messageMapper.selectCount(
                    new LambdaQueryWrapper<SysMessage>()
                            .eq(SysMessage::getUsername, username)
                            .ge(SysMessage::getCreateTime, startOfDay));
            a.setTodayCount(today == null ? 0L : today);
        } catch (Exception e) {
            log.warn("[仪表盘] 消息统计失败 err={}", e.getMessage());
            a.setUnreadCount(0L); a.setTodayCount(0L);
        }
        return a;
    }

    private DashboardStatsResponse.Trends buildTrends() {
        DashboardStatsResponse.Trends t = new DashboardStatsResponse.Trends();
        try {
            t.setDeviceOnline24h(snapshotService.getDeviceOnlineTrend());
            t.setActiveStream24h(snapshotService.getActiveStreamTrend());
        } catch (Exception e) {
            log.warn("[仪表盘] 趋势统计失败 err={}", e.getMessage());
            t.setDeviceOnline24h(Collections.emptyList());
            t.setActiveStream24h(Collections.emptyList());
        }
        return t;
    }

    private List<DashboardStatsResponse.Distribution> buildManufacturerDist() {
        try {
            List<GbDevice> all = deviceMapper.selectList(
                    new LambdaQueryWrapper<GbDevice>().isNotNull(GbDevice::getManufacturer));
            Map<String, Long> cnt = new HashMap<>();
            for (GbDevice d : all) {
                String mfr = d.getManufacturer();
                if (mfr == null || mfr.isBlank()) continue;
                cnt.merge(mfr, 1L, Long::sum);
            }
            List<DashboardStatsResponse.Distribution> out = new ArrayList<>();
            List<DashboardStatsResponse.Distribution> finalOut = out;
            cnt.forEach((k, v) -> {
                DashboardStatsResponse.Distribution dist = new DashboardStatsResponse.Distribution();
                dist.setName(k);
                dist.setValue(v);
                finalOut.add(dist);
            });
            // 数量降序，最多 6 个，超过的折叠为"其他"
            out.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            if (out.size() > 6) {
                long otherSum = 0;
                for (int i = 6; i < out.size(); i++) otherSum += out.get(i).getValue();
                out = new ArrayList<>(out.subList(0, 6));
                DashboardStatsResponse.Distribution other = new DashboardStatsResponse.Distribution();
                other.setName("其他");
                other.setValue(otherSum);
                out.add(other);
            }
            return out;
        } catch (Exception e) {
            log.warn("[仪表盘] 厂商分布失败 err={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 拉一遍全量数据 → 按 serverId group by 计数。任一数据源失败都不影响整体（返回空 Map，
     * 外层 {@code getOrDefault} 会按 0 处理），避免单点故障导致首页 500。
     */
    private <T> Map<String, Long> safeGroupByServerId(
            Supplier<List<T>> source, Function<T, String> keyExtractor) {
        try {
            List<T> list = source.get();
            if (list == null || list.isEmpty()) {
                return Collections.emptyMap();
            }
            return list.stream().collect(Collectors.groupingBy(
                    keyExtractor::apply, Collectors.counting()));
        } catch (Exception e) {
            log.warn("[仪表盘] 流分布 group by 失败 err={}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
