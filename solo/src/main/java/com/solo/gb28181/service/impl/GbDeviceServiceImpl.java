package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.constant.GbRedisKeys;
import com.solo.gb28181.constant.GbSource;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.request.GbDevicePageRequest;
import com.solo.gb28181.response.GbDeviceDetectResponse;
import com.solo.gb28181.response.GbDeviceStatusCountVO;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sip.ResponseEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 国标设备服务实现
 * <p>在线状态双写：MySQL {@code status} 字段 + Redis {@link GbRedisKeys#DEVICE_ONLINE_PREFIX}{deviceId} TTL</p>
 * <p>TTL = max(register_expires, keepalive_default × multiplier)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDeviceServiceImpl extends ServiceImpl<GbDeviceMapper, GbDevice> implements GbDeviceService {

    /** 国标默认 Keepalive 周期（秒） */
    private static final long DEFAULT_KEEPALIVE_INTERVAL = 60L;

    private final StringRedisTemplate redisTemplate;
    private final Gb28181Properties properties;
    private final GbInviteSessionService inviteSessionService;
    private final GbDeviceChannelMapper channelMapper;

    /**
     * {@code @Lazy} 注入打破循环依赖：
     * <pre>
     *   GbDeviceServiceImpl → SipCommander → SipLayer → SipProcessorObserver →
     *   MessageRequestProcessor → KeepaliveNotifyMessageHandler → GbDeviceService
     * </pre>
     * 与 {@link com.solo.gb28181.sip.SipSender} / {@link com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider}
     * 对 {@link com.solo.gb28181.sip.SipLayer} 的处理方式一致。手动探测才会用到，延迟初始化无副作用。
     */
    @Lazy
    @Autowired
    private SipCommander sipCommander;

    @Override
    public Page<GbDevice> pageQuery(GbDevicePageRequest request) {
        LambdaQueryWrapper<GbDevice> wrapper = new LambdaQueryWrapper<>();
        if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
            wrapper.like(GbDevice::getDeviceId, request.getDeviceId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            wrapper.like(GbDevice::getName, request.getName());
        }
        if (request.getStatus() != null) {
            wrapper.eq(GbDevice::getStatus, request.getStatus());
        }
        if (request.getSource() != null) {
            wrapper.eq(GbDevice::getSource, request.getSource());
        }
        wrapper.orderByDesc(GbDevice::getUpdateTime);
        return page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
    }

    @Override
    public List<GbDeviceStatusCountVO> statusCount() {
        return getBaseMapper().selectStatusCount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createByManual(GbDevice device, List<GbDeviceChannel> channels) {
        if (device.getDeviceId() == null || device.getDeviceId().isBlank()) {
            throw new ServiceException("设备国标ID不能为空");
        }
        boolean dup = lambdaQuery().eq(GbDevice::getDeviceId, device.getDeviceId()).exists();
        if (dup) {
            throw new ServiceException("设备国标ID已存在：" + device.getDeviceId());
        }
        // 状态与来源由系统决定，忽略入参
        device.setId(null);
        device.setStatus(GbSource.DEVICE_STATUS_UNREGISTERED);
        device.setSource(GbSource.SYSTEM);
        // 清空 SIP 运行期字段，避免手工录入脏值
        device.setRegisterTime(null);
        device.setKeepaliveTime(null);
        device.setLastCatalogSyncTime(null);
        save(device);
        // 保存同步新增的通道
        log.info("[设备新增] request.channels size = {}", channels != null ? channels.size() : "null");
        if (channels != null && !channels.isEmpty()) {
            for (GbDeviceChannel ch : channels) {
                ch.setId(null);
                ch.setDeviceId(device.getDeviceId());
                ch.setStatus(GbSource.CHANNEL_STATUS_UNREGISTERED);
                ch.setSource(GbSource.SYSTEM);
                ch.setParental(0);  // 手动新增一定是通道
                ch.setParentId(device.getDeviceId());  // parentId = 所属设备 GB 码（与 GB 协议语义一致）
                int rows = channelMapper.insert(ch);
                log.info("[设备新增] save channel channelId={}, rows={}", ch.getChannelId(), rows);
            }
            log.info("[设备新增] 系统手动新增设备 {}，同步新增 {} 条通道", device.getDeviceId(), channels.size());
        } else {
            log.info("[设备新增] 系统手动新增设备: {}", device.getDeviceId());
        }
    }

    @Override
    public void updateByManual(GbDevice device) {
        GbDevice exist = getById(device.getId());
        if (exist == null) {
            throw new ServiceException("设备不存在");
        }
        // 只更新可编辑资料字段，绝不触碰 status/source 及 SIP 运行期字段
        exist.setName(device.getName());
        exist.setManufacturer(device.getManufacturer());
        exist.setModel(device.getModel());
        exist.setFirmware(device.getFirmware());
        exist.setCharset(device.getCharset());
        exist.setTransport(device.getTransport());
        exist.setExpires(device.getExpires());
        updateById(exist);
        evictProfileCache(exist.getDeviceId());
        log.info("[设备编辑] 更新设备资料: {}", exist.getDeviceId());
    }

    @Override
    public void online(GbDevice device) {
        device.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        device.setRegisterTime(now);
        device.setKeepaliveTime(now);

        GbDevice exist = lambdaQuery().eq(GbDevice::getDeviceId, device.getDeviceId()).one();
        if (exist == null) {
            device.setSource(GbSource.REGISTER);
            save(device);
            log.info("[设备上线] 新设备首次注册: {} {}", device.getDeviceId(), device.getHostAddress());
        } else {
            device.setId(exist.getId());
            // 不覆盖 source：保留最初来源（系统新增/导入的设备被真实注册后仍标记为原来源）
            device.setSource(null);
            updateById(device);
            log.info("[设备上线] 重新注册: {} {}", device.getDeviceId(), device.getHostAddress());
        }
        refreshOnlineKey(device.getDeviceId());
        evictProfileCache(device.getDeviceId());
    }

    @Override
    public void offline(String deviceId, String reason) {
        GbDevice device = lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device != null && device.getStatus() != null && device.getStatus() == 1) {
            device.setStatus(0);
            updateById(device);
        }
        redisTemplate.delete(GbRedisKeys.DEVICE_ONLINE_PREFIX + deviceId);
        evictProfileCache(deviceId);

        // 设备离线，清扫该设备所有活跃点播/回放会话，释放 SSRC / RTP 端口 / Redis 资源
        List<GbInviteSession> allSessions = inviteSessionService.listAllActive();
        int cleaned = 0;
        for (GbInviteSession s : allSessions) {
            if (deviceId.equals(s.getDeviceId())) {
                inviteSessionService.removeByStreamId(s.getStreamId());
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.info("[设备离线] 已同步清理该设备 {} 个活跃会话", cleaned);
        }

        log.info("[设备离线] {} reason={}", deviceId, reason);
    }

    @Override
    public void keepalive(GbDevice device) {
        device.setKeepaliveTime(LocalDateTime.now());
        if (device.getStatus() == null || device.getStatus() != 1) {
            device.setStatus(1);
        }
        updateById(device);
        refreshOnlineKey(device.getDeviceId());
    }

    private void refreshOnlineKey(String deviceId) {
        long ttl = DEFAULT_KEEPALIVE_INTERVAL * Math.max(1, properties.getKeepaliveTimeoutMultiplier());
        redisTemplate.opsForValue().set(GbRedisKeys.DEVICE_ONLINE_PREFIX + deviceId, "1", ttl, TimeUnit.SECONDS);
    }

    @Override
    public GbDeviceDetectResponse detectBatch(List<Long> ids) {
        GbDeviceDetectResponse result = new GbDeviceDetectResponse();
        if (ids == null || ids.isEmpty()) {
            return result;
        }

        long detectTimeoutMs = properties.getDetectTimeoutMs();

        for (Long id : ids) {
            GbDevice device = baseMapper.selectById(id);
            if (device == null) {
                continue;
            }
            // 无网络地址（从未注册）无法发包探测，跳过且不改 status
            if (device.getHostAddress() == null || device.getHostAddress().isBlank()) {
                result.setSkipped(result.getSkipped() + 1);
                result.getItems().add(new GbDeviceDetectResponse.Item(id, device.getDeviceId(), "skipped"));
                log.info("[设备检测] 跳过（无网络地址）deviceId={}", device.getDeviceId());
                continue;
            }

            // 保存 deviceId 快照供回调使用（避免 device 对象被后续循环覆盖）
            final String deviceId = device.getDeviceId();
            final Long deviceDbId = device.getId();

            // 收到设备应答 → 在线
            Consumer<ResponseEvent> onOk = ev -> markOnline(deviceId, deviceDbId);
            // 设备回非 2xx 仍是存活（只是查询被拒）
            Consumer<ResponseEvent> onError = ev -> markOnline(deviceId, deviceDbId);
            // 超时无应答 → 离线
            Runnable onTimeout = () -> markOffline(deviceId, deviceDbId);

            sipCommander.queryDeviceInfo(device, onOk, onError, onTimeout, detectTimeoutMs);
            result.getItems().add(new GbDeviceDetectResponse.Item(id, device.getDeviceId(), "detecting"));
            result.setDetecting(result.getDetecting() + 1);
        }
        log.info("[设备检测] 发起 {} 台检测，跳过 {} 台", result.getDetecting(), result.getSkipped());
        return result;
    }

    /** 异步标记设备在线 */
    private void markOnline(String deviceId, Long deviceDbId) {
        try {
            GbDevice device = new GbDevice();
            device.setId(deviceDbId);
            device.setStatus(1);
            device.setKeepaliveTime(LocalDateTime.now());
            baseMapper.updateById(device);
            refreshOnlineKey(deviceId);
            log.info("[设备检测] 在线 deviceId={}", deviceId);
        } catch (Exception e) {
            log.error("[设备检测] 标记在线失败 deviceId={}", deviceId, e);
        }
    }

    /** 异步标记设备离线 */
    private void markOffline(String deviceId, Long deviceDbId) {
        try {
            GbDevice device = new GbDevice();
            device.setId(deviceDbId);
            device.setStatus(0);
            baseMapper.updateById(device);
            redisTemplate.delete(GbRedisKeys.DEVICE_ONLINE_PREFIX + deviceId);
            cleanupSessions(deviceId);
            log.info("[设备检测] 离线 deviceId={}", deviceId);
        } catch (Exception e) {
            log.error("[设备检测] 标记离线失败 deviceId={}", deviceId, e);
        }
    }

    /**
     * 清扫设备所有活跃点播/回放会话（释放 SSRC / RTP 端口 / Redis 资源）
     */
    private void cleanupSessions(String deviceId) {
        List<GbInviteSession> allSessions = inviteSessionService.listAllActive();
        int cleaned = 0;
        for (GbInviteSession s : allSessions) {
            if (deviceId.equals(s.getDeviceId())) {
                inviteSessionService.removeByStreamId(s.getStreamId());
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.info("[设备离线] 已同步清理该设备 {} 个活跃会话", cleaned);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            GbDevice device = getById(id);
            if (device != null) {
                // 删除该设备下所有通道（按 GB 码）
                channelMapper.delete(new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getDeviceId, device.getDeviceId()));
                // 删除设备
                removeById(id);
                // 删除 Redis 在线状态键
                redisTemplate.delete(GbRedisKeys.DEVICE_ONLINE_PREFIX + device.getDeviceId());
                evictProfileCache(device.getDeviceId());
                log.info("[批量删除] 删除设备: deviceId={}, name={}", device.getDeviceId(), device.getName());
            }
        }
    }

    @Override
    public void evictProfileCache(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(GbRedisKeys.DEVICE_PROFILE_PREFIX + deviceId);
        } catch (Exception e) {
            // 缓存失效失败不阻塞业务，等 TTL 兜底
            log.warn("[设备档案] 缓存失效失败 deviceId={} err={}", deviceId, e.getMessage());
        }
    }

    @Override
    public Page<GbDevice> searchByKeyword(String keyword, Long page, Long pageSize) {
        LambdaQueryWrapper<GbDevice> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w ->
                w.like(GbDevice::getDeviceId, keyword)
                 .or()
                 .like(GbDevice::getName, keyword)
                 .or()
                 .like(GbDevice::getReportName, keyword)
            );
        }
        wrapper.orderByDesc(GbDevice::getUpdateTime);
        return page(new Page<>(page, pageSize), wrapper);
    }
}
