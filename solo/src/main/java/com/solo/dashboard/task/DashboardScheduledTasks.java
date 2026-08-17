package com.solo.dashboard.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.dashboard.service.StatsSnapshotService;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.media.service.MediaServerLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 仪表盘定时任务统一入口
 *
 * <h3>调度</h3>
 * <ul>
 *   <li>每小时 1 次 → 写入 24h 趋势桶（设备在线数 + 活跃流数）</li>
 * </ul>
 *
 * <p>调度总开关 {@code @EnableScheduling} 在 {@code SoloApplication} 上开启。
 * 沿用 {@code com.solo.gb28181.task.GbScheduledTasks} 与
 * {@code com.solo.media.task.MediaScheduledTasks} 的双文件约定：
 * 业务类只保留纯业务方法（{@link StatsSnapshotService#snapshot}），本类持有触发点。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardScheduledTasks {

    private final StatsSnapshotService snapshotService;
    private final GbDeviceMapper deviceMapper;
    private final MediaServerLoader mediaServerLoader;

    /**
     * 每小时一次写入 24h 趋势桶。
     * 初始延迟 60s 避免启动期任务并发打堆；周期 1h。
     */
    @Scheduled(initialDelay = 60_000, fixedRate = 3600_000)
    public void snapshotTrend() {
        try {
            long onlineDevices = deviceMapper.selectCount(
                    new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getStatus, 1));
            long activeStreams = mediaServerLoader.getTotalLoad();
            snapshotService.snapshot(onlineDevices, activeStreams);
            log.debug("[仪表盘] 趋势快照完成 onlineDevices={} activeStreams={}",
                    onlineDevices, activeStreams);
        } catch (Exception e) {
            log.warn("[仪表盘] 趋势快照异常 {}", e.getMessage());
        }
    }
}
