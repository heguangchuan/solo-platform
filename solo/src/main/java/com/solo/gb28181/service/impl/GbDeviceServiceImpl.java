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
 * <p>除了写库，还把"在线"信号写到 Redis，TTL = keepaliveTimeoutMultiplier × Keepalive 周期（默认 60s × 3）</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDeviceServiceImpl extends ServiceImpl<GbDeviceMapper, GbDevice> implements GbDeviceService {

    /** Redis 中设备在线状态 key 前缀 */
    private static final String ONLINE_KEY_PREFIX = "gb28181:device:online:";
    /** Keepalive 默认周期（秒，国标推荐 60） */
    private static final long DEFAULT_KEEPALIVE_INTERVAL_SECONDS = 60L;

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
    public void onRegister(String deviceId, String ip, int port, String transport, int expires) {
        GbDevice device = getByDeviceId(deviceId);
        LocalDateTime now = LocalDateTime.now();
        if (device == null) {
            device = new GbDevice();
            device.setDeviceId(deviceId);
            device.setSipDomain(properties.getDomain());
            device.setIp(ip);
            device.setPort(port);
            device.setTransport(transport);
            device.setExpires(expires);
            device.setStatus(1);
            device.setRegisterTime(now);
            device.setKeepaliveTime(now);
            save(device);
            log.info("[GB28181] 新设备注册: deviceId={} {}:{} {}", deviceId, ip, port, transport);
        } else {
            device.setIp(ip);
            device.setPort(port);
            device.setTransport(transport);
            device.setExpires(expires);
            device.setStatus(1);
            device.setRegisterTime(now);
            device.setKeepaliveTime(now);
            updateById(device);
            log.info("[GB28181] 设备重新注册: deviceId={} {}:{} {}", deviceId, ip, port, transport);
        }
        markOnline(deviceId);
    }

    @Override
    public void onUnregister(String deviceId) {
        GbDevice device = getByDeviceId(deviceId);
        if (device != null) {
            device.setStatus(0);
            updateById(device);
        }
        redisTemplate.delete(ONLINE_KEY_PREFIX + deviceId);
        log.info("[GB28181] 设备注销: deviceId={}", deviceId);
    }

    @Override
    public void onKeepalive(String deviceId) {
        GbDevice device = getByDeviceId(deviceId);
        if (device == null) {
            log.warn("[GB28181] 收到未知设备心跳: deviceId={}", deviceId);
            return;
        }
        device.setKeepaliveTime(LocalDateTime.now());
        if (device.getStatus() == null || device.getStatus() != 1) {
            device.setStatus(1);
        }
        updateById(device);
        markOnline(deviceId);
    }

    private void markOnline(String deviceId) {
        long ttl = DEFAULT_KEEPALIVE_INTERVAL_SECONDS * Math.max(1, properties.getKeepaliveTimeoutMultiplier());
        redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + deviceId, "1", ttl, TimeUnit.SECONDS);
    }
}
