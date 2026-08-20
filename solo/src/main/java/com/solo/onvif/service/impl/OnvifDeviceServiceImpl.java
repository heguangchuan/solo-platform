package com.solo.onvif.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.onvif.client.OnvifClient;
import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.mapper.OnvifDeviceMapper;
import com.solo.onvif.request.OnvifDevicePageRequest;
import com.solo.onvif.service.OnvifDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ONVIF 设备服务实现
 * <p>状态权威由 {@link com.solo.onvif.task.OnvifDeviceStatusTask} 定时探测更新，与国标"SIP 交互改状态"路径不同。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnvifDeviceServiceImpl extends ServiceImpl<OnvifDeviceMapper, OnvifDevice> implements OnvifDeviceService {

    @Override
    public Page<OnvifDevice> pageQuery(OnvifDevicePageRequest req) {
        LambdaQueryWrapper<OnvifDevice> wrapper = new LambdaQueryWrapper<>();
        if (req.getName() != null && !req.getName().isBlank()) {
            wrapper.like(OnvifDevice::getName, req.getName());
        }
        if (req.getHost() != null && !req.getHost().isBlank()) {
            wrapper.like(OnvifDevice::getHost, req.getHost());
        }
        if (req.getStatus() != null) {
            wrapper.eq(OnvifDevice::getStatus, req.getStatus());
        }
        return page(new Page<>(req.getPage(), req.getPageSize()), wrapper);
    }

    @Override
    public void saveWithProbe(OnvifDevice device) {
        if (device.getHost() == null || device.getHost().isBlank()) {
            throw new ServiceException("设备 IP 不能为空");
        }
        // 端口默认 80
        if (device.getPort() == null || device.getPort() <= 0) {
            device.setPort(80);
        }
        if (device.getId() == null) {
            device.setStatus(0);
            saveOrUpdate(device);
            log.info("[ONVIF设备] 新增 主机: {}:{} 名称: {}", device.getHost(), device.getPort(), device.getName());
        } else {
            // 编辑：只覆盖用户可改字段，不动状态/来源/探测填充结果
            OnvifDevice old = getById(device.getId());
            if (old == null) {
                throw new ServiceException("设备不存在: " + device.getId());
            }
            old.setName(device.getName());
            old.setHost(device.getHost());
            old.setPort(device.getPort());
            old.setUsername(device.getUsername());
            old.setPassword(device.getPassword());
            // 关键信息可能变了，清掉过时的探测结果
            old.setStreamUri(null);
            updateById(old);
            device.setId(old.getId());
            log.info("[ONVIF设备] 编辑 id: {} 主机: {}:{}", old.getId(), old.getHost(), old.getPort());
        }
        // 同步探测一次
        try {
            probeInternal(device);
        } catch (Exception e) {
            log.warn("[ONVIF探测] 保存后同步探测失败 id: {} 错误: {}", device.getId(), e.getMessage());
            // 探测失败不影响保存，日志里可查
        }
    }

    @Override
    public void probe(Long id) {
        OnvifDevice device = getById(id);
        if (device == null) {
            throw new ServiceException("设备不存在: " + id);
        }
        probeInternal(device);
    }

    /**
     * 探测逻辑：TCP ping → SOAP 探测 → 更新 status / streamUri / lastCheckTime。
     * 厂商/型号/固件/序列号只打日志，不落库。
     */
    @Override
    public void probeInternal(OnvifDevice device) {
        LocalDateTime now = LocalDateTime.now();
        device.setLastCheckTime(now);
        if (!OnvifClient.ping(device.getHost(), device.getPort(), 3000)) {
            device.setStatus(0);
            device.setStreamUri(null);
            updateById(device);
            log.info("[ONVIF探测] {}:{} TCP 不通 | 最后探测时间: {}", device.getHost(), device.getPort(), now);
            return;
        }
        try {
            OnvifClient client = new OnvifClient(device.getHost(), device.getPort(),
                    device.getUsername(), device.getPassword());
            OnvifClient.OnvifProbeResult r = client.probe();
            device.setStreamUri(r.getStreamUri());
            device.setStatus(1);
            // 基本信息只打日志，不写入 DB
            log.info("[ONVIF探测] 成功 {}:{} 厂商: {} 型号: {} 固件版本: {} 序列号: {} 流地址: {} | 最后探测时间: {}",
                    device.getHost(), device.getPort(), r.getManufacturer(), r.getModel(),
                    r.getFirmware(), r.getSerialNumber(), r.getStreamUri(), now);
        } catch (Exception e) {
            device.setStatus(0);
            device.setStreamUri(null);
            log.info("[ONVIF探测] 失败 {}:{} 错误: {} | 最后探测时间: {}",
                    device.getHost(), device.getPort(), e.getMessage(), now);
        }
        updateById(device);
    }
}
