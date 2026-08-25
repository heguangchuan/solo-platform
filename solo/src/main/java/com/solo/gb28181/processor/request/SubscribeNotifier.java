package com.solo.gb28181.processor.request;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.event.alarm.AlarmEvent;
import com.solo.gb28181.event.alarm.DeviceAlarm;
import com.solo.gb28181.event.catalog.CatalogSyncProgressEvent;
import com.solo.gb28181.event.device.DeviceOfflineEvent;
import com.solo.gb28181.event.device.DeviceOnlineEvent;
import com.solo.gb28181.event.position.DevicePosition;
import com.solo.gb28181.event.position.PositionEvent;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.support.CatalogSyncProgress;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订阅 NOTIFY 推送路由器（ADR-030）。
 *
 * <p>监听设备/位置/报警三类事件，按 {@link SubscribeHolder} 里登记的订阅关系把
 * 报文转给 {@link PlatformCommander} 发向上级平台。</p>
 *
 * <h3>订阅匹配规则</h3>
 * <p>订阅时 {@link SubscribeInfo#deviceId} 为空 → 订阅本平台全部设备；非空 → 仅订阅该 deviceId。
 * 推送时按 deviceId 精确匹配 + 空匹配（订阅全部）两种放行，其它跳过。</p>
 *
 * <h3>异步执行</h3>
 * <p>所有推送走 {@code gb28181BizExecutor} 异步线程池，{@link PlatformCommander#sendNotifyCatalogItem}
 * 等方法内部再 submit 一次，{@code @Async} 是入口处的解耦——事件发布者（Catalog 同步、设备心跳等）
 * 不应该被 NOTIFY 推送阻塞，NOTIFY 失败也不应回滚业务事件。</p>
 *
 * <h3>不重复推送</h3>
 * <ul>
 *   <li>Catalog 同步完成（{@code CatalogSyncProgressEvent} 走 NOTIFY 多通道推送）——本类不重复触发
 *       单设备推送，避免双发</li>
 *   <li>设备首次上线 + Catalog 同步 → 单设备 ON 推送一次（由 {@link #onDeviceOnline} 触发）</li>
 * </ul>
 *
 * <h3>不实现</h3>
 * <ul>
 *   <li>订阅初始 NOTIFY（SUBSCRIBE 后立即报当前状态）——RFC 6665 推荐但 GB 设备实现各异；当前
 *       只做事件驱动，订阅方想要快照可主动发 Query</li>
 *   <li>订阅过期清扫——{@link SubscribeHolder} 是 in-memory，重启清空；上级按自身重传机制兜底</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscribeNotifier {

    private final SubscribeHolder subscribeHolder;
    private final GbParentPlatformService gbParentPlatformService;
    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final PlatformCommander platformCommander;

    // ==================== Catalog 推送 ====================

    @Async("gb28181BizExecutor")
    @EventListener
    public void onDeviceOnline(DeviceOnlineEvent event) {
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, event.getDeviceId()));
        if (device == null) {
            return;
        }
        // 上线即视为 Status=ON，推一次单设备 NOTIFY
        fanoutCatalogItem("Catalog", event.getDeviceId(), device, null);
    }

    @Async("gb28181BizExecutor")
    @EventListener
    public void onDeviceOffline(DeviceOfflineEvent event) {
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, event.getDeviceId()));
        if (device == null) {
            return;
        }
        // 离线：Status=OFF，推一次单设备 NOTIFY
        fanoutCatalogItem("Catalog", event.getDeviceId(), device, null);
    }

    /**
     * 通道目录同步完成——按订阅维度推送变更
     * <p>同步完成时设备肯定在线；通道集合里 status 字段由 CatalogDataCache 写入时按 ON 标，
     * 离线设备同步会跳过（{@code CatalogDataCache.cancel}），所以这里只推 ON 集合。
     * 但订阅方关心"通道删除了吗"——所以即使空集合也推：清空表示该设备全通道被移除。</p>
     */
    @Async("gb28181BizExecutor")
    @EventListener
    public void onCatalogSynced(CatalogSyncProgressEvent event) {
        CatalogSyncProgress progress = event.getProgress();
        if (progress == null) {
            return;
        }
        // 只在终态触发推送：SUCCESS / FAILED；IDLE / RUNNING 期间不推（半成品 + 漏报都会污染订阅方视图）
        String status = progress.getStatus();
        if (!CatalogSyncProgress.STATUS_SUCCESS.equals(status)
                && !CatalogSyncProgress.STATUS_FAILED.equals(status)) {
            return;
        }
        String deviceId = progress.getDeviceId();
        // 拉当前设备的所有通道（gb_device_channel 物理表是 Catalog 同步的唯一权威）
        List<GbDeviceChannel> channels = channelMapper.selectList(
                new LambdaQueryWrapper<GbDeviceChannel>().eq(GbDeviceChannel::getDeviceId, deviceId));
        // 即便空集合也推送（让订阅方知道该设备无通道或通道全删）
        fanoutCatalogList(deviceId, channels, null);
    }

    // ==================== MobilePosition 推送 ====================

    @Async("gb28181BizExecutor")
    @EventListener
    public void onPosition(PositionEvent event) {
        DevicePosition pos = event.getPosition();
        if (pos == null || pos.getDeviceId() == null) {
            return;
        }
        fanoutPosition(pos, null);
    }

    // ==================== Alarm 推送 ====================

    @Async("gb28181BizExecutor")
    @EventListener
    public void onAlarm(AlarmEvent event) {
        DeviceAlarm alarm = event.getAlarm();
        if (alarm == null) {
            return;
        }
        // Alarm.deviceId 可能是设备或通道——但订阅方订阅 deviceId 时通常指设备，所以同时检查
        // device 与 channel 两个维度匹配
        fanoutAlarm(alarm, null);
    }

    // ==================== fanout helpers ====================

    private void fanoutCatalogItem(String cmdType, String deviceId, GbDevice device, String fallbackSn) {
        for (SubscribeInfo sub : subscribeHolder.listByCmdType(cmdType)) {
            if (!matches(sub, deviceId)) continue;
            GbParentPlatform platform = platformOf(sub);
            if (platform == null) continue;
            String sn = sub.getSn() != null ? sub.getSn() : (fallbackSn != null ? fallbackSn : String.valueOf(PlatformCommander.nextSn()));
            platformCommander.sendNotifyCatalogItem(platform, device, sn);
        }
    }

    private void fanoutCatalogList(String deviceId, List<GbDeviceChannel> channels, String fallbackSn) {
        for (SubscribeInfo sub : subscribeHolder.listByCmdType("Catalog")) {
            if (!matches(sub, deviceId)) continue;
            GbParentPlatform platform = platformOf(sub);
            if (platform == null) continue;
            String sn = sub.getSn() != null ? sub.getSn() : (fallbackSn != null ? fallbackSn : String.valueOf(PlatformCommander.nextSn()));
            platformCommander.sendNotifyCatalogList(platform, deviceId, channels, sn);
        }
    }

    private void fanoutPosition(DevicePosition pos, String fallbackSn) {
        for (SubscribeInfo sub : subscribeHolder.listByCmdType("MobilePosition")) {
            if (!matches(sub, pos.getDeviceId())) continue;
            GbParentPlatform platform = platformOf(sub);
            if (platform == null) continue;
            String sn = sub.getSn() != null ? sub.getSn() : (fallbackSn != null ? fallbackSn : String.valueOf(PlatformCommander.nextSn()));
            platformCommander.sendNotifyMobilePosition(platform, pos, sn);
        }
    }

    private void fanoutAlarm(DeviceAlarm alarm, String fallbackSn) {
        // Alarm 的 deviceId 可能是通道 ID；优先按 device 维度查订阅，匹配失败再按 channel 维度
        String alarmDeviceId = alarm.getDeviceId();
        String alarmChannelId = alarm.getChannelId();
        for (SubscribeInfo sub : subscribeHolder.listByCmdType("Alarm")) {
            boolean hit = false;
            if (matches(sub, alarmDeviceId)) hit = true;
            else if (alarmChannelId != null && matches(sub, alarmChannelId)) hit = true;
            if (!hit) continue;
            GbParentPlatform platform = platformOf(sub);
            if (platform == null) continue;
            String sn = sub.getSn() != null ? sub.getSn() : (fallbackSn != null ? fallbackSn : String.valueOf(PlatformCommander.nextSn()));
            platformCommander.sendNotifyAlarm(platform, alarm, sn);
        }
    }

    /** 订阅匹配：空 deviceId = 订阅全部；非空 = 精确匹配 */
    private static boolean matches(SubscribeInfo sub, String deviceId) {
        if (sub == null || sub.getDeviceId() == null || sub.getDeviceId().isEmpty()) {
            return true; // 订阅全部
        }
        return sub.getDeviceId().equals(deviceId);
    }

    /** 订阅方平台配置——可能已被禁用/删除，按 enabled=1 过滤 */
    private GbParentPlatform platformOf(SubscribeInfo sub) {
        return gbParentPlatformService.lambdaQuery()
                .eq(GbParentPlatform::getServerGbId, sub.getPlatformId())
                .eq(GbParentPlatform::getEnabled, 1)
                .one();
    }
}
