package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.request.GbDevicePageRequest;
import com.solo.gb28181.service.GbDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 国标设备服务实现
 * <p>在线状态双写：MySQL {@code status} 字段 + Redis {@code gb28181:device:online:{id}} TTL</p>
 * <p>TTL = max(register_expires, keepalive_default × multiplier)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDeviceServiceImpl extends ServiceImpl<GbDeviceMapper, GbDevice> implements GbDeviceService {

    private static final String ONLINE_KEY_PREFIX = "gb28181:device:online:";
    /** 国标默认 Keepalive 周期（秒） */
    private static final long DEFAULT_KEEPALIVE_INTERVAL = 60L;

    private final StringRedisTemplate redisTemplate;
    private final Gb28181Properties properties;

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
        wrapper.orderByDesc(GbDevice::getUpdateTime);
        return page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
    }

    @Override
    public GbDevice getByDeviceId(String deviceId) {
        return lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
    }

    @Override
    public void online(GbDevice device) {
        device.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        device.setRegisterTime(now);
        device.setKeepaliveTime(now);

        GbDevice exist = getByDeviceId(device.getDeviceId());
        if (exist == null) {
            save(device);
            log.info("[设备上线] 新设备首次注册: {} {}", device.getDeviceId(), device.getHostAddress());
        } else {
            device.setId(exist.getId());
            updateById(device);
            log.info("[设备上线] 重新注册: {} {}", device.getDeviceId(), device.getHostAddress());
        }
        refreshOnlineKey(device.getDeviceId());
    }

    @Override
    public void offline(String deviceId, String reason) {
        GbDevice device = getByDeviceId(deviceId);
        if (device != null && device.getStatus() != null && device.getStatus() == 1) {
            device.setStatus(0);
            updateById(device);
        }
        redisTemplate.delete(ONLINE_KEY_PREFIX + deviceId);
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
        long expires = properties.getRegisterExpires();
        long heartbeatTtl = DEFAULT_KEEPALIVE_INTERVAL * Math.max(1, properties.getKeepaliveTimeoutMultiplier());
        long ttl = Math.max(expires, heartbeatTtl);
        redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + deviceId, "1", ttl, TimeUnit.SECONDS);
    }
}
