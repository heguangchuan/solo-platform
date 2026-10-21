package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDeviceAlarm;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.event.alarm.DeviceAlarm;
import com.solo.gb28181.mapper.GbDeviceAlarmMapper;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.request.GbDeviceAlarmHandleRequest;
import com.solo.gb28181.request.GbDeviceAlarmPageRequest;
import com.solo.gb28181.service.GbDeviceAlarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbDeviceAlarmServiceImpl
        extends ServiceImpl<GbDeviceAlarmMapper, GbDeviceAlarm> implements GbDeviceAlarmService {

    /** 允许通过处置接口设置的状态 */
    private static final Set<Integer> VALID_HANDLE_STATUS = Set.of(1, 2, 3);

    private final GbDeviceChannelMapper channelMapper;

    @Override
    public Page<GbDeviceAlarm> pageQuery(GbDeviceAlarmPageRequest request) {
        LambdaQueryWrapper<GbDeviceAlarm> wrapper = new LambdaQueryWrapper<>();
        if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
            wrapper.like(GbDeviceAlarm::getDeviceId, request.getDeviceId());
        }
        if (request.getChannelId() != null && !request.getChannelId().isBlank()) {
            wrapper.like(GbDeviceAlarm::getChannelId, request.getChannelId());
        }
        if (request.getAlarmPriority() != null) {
            wrapper.eq(GbDeviceAlarm::getAlarmPriority, request.getAlarmPriority());
        }
        if (request.getHandleStatus() != null) {
            wrapper.eq(GbDeviceAlarm::getHandleStatus, request.getHandleStatus());
        }
        if (request.getStartTime() != null) {
            wrapper.ge(GbDeviceAlarm::getAlarmTime, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(GbDeviceAlarm::getAlarmTime, request.getEndTime());
        }
        // 报警时间可能缺失（非法报文），用入库时间兜底排序
        wrapper.orderByDesc(GbDeviceAlarm::getCreateTime);
        return page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
    }

    @Override
    public void handle(GbDeviceAlarmHandleRequest request, String operator) {
        if (request.getId() == null) {
            throw new ServiceException("报警 ID 不能为空");
        }
        if (request.getHandleStatus() == null || !VALID_HANDLE_STATUS.contains(request.getHandleStatus())) {
            throw new ServiceException("处置状态非法");
        }
        GbDeviceAlarm alarm = getById(request.getId());
        if (alarm == null) {
            throw new ServiceException("报警不存在");
        }
        alarm.setHandleStatus(request.getHandleStatus());
        alarm.setHandleRemark(request.getHandleRemark());
        alarm.setHandleUser(operator);
        alarm.setHandleTime(LocalDateTime.now());
        updateById(alarm);
        log.info("[报警中心] 处置报警 id={} status={} operator={}", request.getId(),
                request.getHandleStatus(), operator);
    }

    @Override
    public void deleteAlarm(Long id) {
        if (!removeById(id)) {
            throw new ServiceException("报警不存在");
        }
    }

    @Override
    public GbDeviceAlarm saveFromEvent(DeviceAlarm source) {
        GbDeviceAlarm alarm = new GbDeviceAlarm();
        alarm.setDeviceId(source.getDeviceId());
        alarm.setChannelId(source.getChannelId());
        alarm.setChannelName(resolveChannelName(source.getDeviceId(), source.getChannelId()));
        alarm.setAlarmPriority(parseIntOrNull(source.getAlarmPriority()));
        alarm.setAlarmMethod(source.getAlarmMethod());
        alarm.setAlarmMethodDesc(source.getAlarmMethodDesc());
        alarm.setAlarmType(source.getAlarmType());
        alarm.setAlarmTime(source.getAlarmTime());
        alarm.setAlarmDescription(source.getAlarmDescription());
        alarm.setLongitude(source.getLongitude());
        alarm.setLatitude(source.getLatitude());
        alarm.setReceiveTime(source.getReceiveTime());
        alarm.setHandleStatus(0);
        save(alarm);
        return alarm;
    }

    /**
     * 按设备国标 ID + 通道国标 ID 反查通道名称，查不到返回 null（设备可能已删除）
     */
    private String resolveChannelName(String deviceId, String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return null;
        }
        GbDeviceChannel channel = channelMapper.selectOne(new LambdaQueryWrapper<GbDeviceChannel>()
                .eq(GbDeviceChannel::getDeviceId, deviceId)
                .eq(GbDeviceChannel::getChannelId, channelId)
                .last("LIMIT 1"));
        if (channel == null) {
            return null;
        }
        // 与设备详情通道表保持一致：优先设备上报名称，其次通道命名，最后国标 ID
        if (channel.getReportName() != null && !channel.getReportName().isBlank()) {
            return channel.getReportName();
        }
        if (channel.getName() != null && !channel.getName().isBlank()) {
            return channel.getName();
        }
        return channel.getChannelId();
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
