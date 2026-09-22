package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDeviceAlarm;
import com.solo.gb28181.event.alarm.DeviceAlarm;
import com.solo.gb28181.request.GbDeviceAlarmHandleRequest;
import com.solo.gb28181.request.GbDeviceAlarmPageRequest;

/**
 * 设备报警服务
 */
public interface GbDeviceAlarmService extends IService<GbDeviceAlarm> {

    /**
     * 分页查询报警
     */
    Page<GbDeviceAlarm> pageQuery(GbDeviceAlarmPageRequest request);

    /**
     * 处置报警（确认 / 已处置 / 误报）
     *
     * @param operator 操作人用户名
     */
    void handle(GbDeviceAlarmHandleRequest request, String operator);

    /**
     * 删除报警
     */
    void deleteAlarm(Long id);

    /**
     * 将 SIP 事件层 DTO 落库（含通道名称冗余）
     *
     * @return 落库后的报警实体
     */
    GbDeviceAlarm saveFromEvent(DeviceAlarm source);
}
