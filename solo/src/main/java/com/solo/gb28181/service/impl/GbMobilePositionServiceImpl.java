package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbMobilePosition;
import com.solo.gb28181.event.position.DevicePosition;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbMobilePositionMapper;
import com.solo.gb28181.request.GbMobilePositionPageRequest;
import com.solo.gb28181.service.GbMobilePositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbMobilePositionServiceImpl
        extends ServiceImpl<GbMobilePositionMapper, GbMobilePosition> implements GbMobilePositionService {

    /** 单次轨迹查询返回的最大点数，防止高频上报设备一次拉全表 */
    private static final long TRACK_LIMIT = 10_000L;

    private final GbDeviceMapper deviceMapper;

    @Override
    public Page<GbMobilePosition> pageQuery(GbMobilePositionPageRequest request) {
        LambdaQueryWrapper<GbMobilePosition> wrapper = buildWrapper(request);
        // 设备定位时间可能缺失（非法报文），用入库时间兜底排序
        wrapper.orderByDesc(GbMobilePosition::getCreateTime);
        return page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
    }

    @Override
    public List<GbMobilePosition> track(GbMobilePositionPageRequest request) {
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            throw new ServiceException("设备国标 ID 不能为空");
        }
        LambdaQueryWrapper<GbMobilePosition> wrapper = buildWrapper(request);
        wrapper.orderByAsc(GbMobilePosition::getPositionTime).last("LIMIT " + TRACK_LIMIT);
        return list(wrapper);
    }

    @Override
    public void deletePosition(Long id) {
        if (!removeById(id)) {
            throw new ServiceException("轨迹点不存在");
        }
    }

    @Override
    public GbMobilePosition saveFromEvent(DevicePosition source) {
        GbMobilePosition position = new GbMobilePosition();
        position.setDeviceId(source.getDeviceId());
        position.setDeviceName(resolveDeviceName(source.getDeviceId()));
        position.setPositionTime(source.getTime());
        position.setLongitude(source.getLongitude());
        position.setLatitude(source.getLatitude());
        position.setAltitude(source.getAltitude());
        position.setSpeed(source.getSpeed());
        position.setDirection(source.getDirection());
        position.setReceiveTime(source.getReceiveTime());
        save(position);
        return position;
    }

    @Override
    public int cleanExpired(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        return baseMapper.delete(new LambdaQueryWrapper<GbMobilePosition>()
                .lt(GbMobilePosition::getCreateTime, threshold));
    }

    private LambdaQueryWrapper<GbMobilePosition> buildWrapper(GbMobilePositionPageRequest request) {
        LambdaQueryWrapper<GbMobilePosition> wrapper = new LambdaQueryWrapper<>();
        // 前端通过设备下拉选择（GbDeviceSelect），按设备国标 ID 精确匹配
        if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
            wrapper.eq(GbMobilePosition::getDeviceId, request.getDeviceId().trim());
        }
        if (request.getStartTime() != null) {
            wrapper.ge(GbMobilePosition::getPositionTime, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(GbMobilePosition::getPositionTime, request.getEndTime());
        }
        return wrapper;
    }

    /**
     * 按设备国标 ID 反查设备名称：优先用户自定义名称，其次上报名称，查不到返回 null
     */
    private String resolveDeviceName(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        GbDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<GbDevice>()
                .eq(GbDevice::getDeviceId, deviceId)
                .last("LIMIT 1"));
        if (device == null) {
            return null;
        }
        if (device.getName() != null && !device.getName().isBlank()) {
            return device.getName();
        }
        if (device.getReportName() != null && !device.getReportName().isBlank()) {
            return device.getReportName();
        }
        return null;
    }
}
