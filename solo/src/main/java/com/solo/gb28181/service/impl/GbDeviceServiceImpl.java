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
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.request.GbDevicePageRequest;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbInviteSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private final GbDeviceChannelService deviceChannelService;

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
                ch.setDeviceDbId(device.getId());
                ch.setDeviceId(device.getDeviceId());
                ch.setStatus(GbSource.CHANNEL_STATUS_UNREGISTERED);
                ch.setSource(GbSource.SYSTEM);
                ch.setPlatformId(device.getPlatformId() != null ? device.getPlatformId() : "0000");
                ch.setGroupId(ch.getGroupId() != null && !ch.getGroupId().isBlank()
                        ? ch.getGroupId() : properties.getDefaultGroup().getId());
                boolean saved = deviceChannelService.save(ch);
                log.info("[设备新增] save channel channelId={}, result={}", ch.getChannelId(), saved);
            }
            // 更新设备通道计数
            long count = deviceChannelService.lambdaQuery()
                    .eq(GbDeviceChannel::getDeviceDbId, device.getId())
                    .count();
            device.setChannelCount((int) count);
            updateById(device);
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
    }

    @Override
    public void offline(String deviceId, String reason) {
        GbDevice device = lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device != null && device.getStatus() != null && device.getStatus() == 1) {
            device.setStatus(0);
            updateById(device);
        }
        redisTemplate.delete(GbRedisKeys.DEVICE_ONLINE_PREFIX + deviceId);

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
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            GbDevice device = getById(id);
            if (device != null) {
                // 删除该设备下所有通道
                deviceChannelService.deleteByDeviceDbId(id);
                // 删除设备
                removeById(id);
                // 删除 Redis 在线状态键
                redisTemplate.delete(GbRedisKeys.DEVICE_ONLINE_PREFIX + device.getDeviceId());
                log.info("[批量删除] 删除设备: deviceId={}, name={}", device.getDeviceId(), device.getName());
            }
        }
    }
}
