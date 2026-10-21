package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDeviceAlarm;
import com.solo.gb28181.event.alarm.DeviceAlarm;
import com.solo.gb28181.request.GbDeviceAlarmHandleRequest;
import com.solo.gb28181.request.GbDeviceAlarmPageRequest;

public interface GbDeviceAlarmService extends IService<GbDeviceAlarm> {

    Page<GbDeviceAlarm> pageQuery(GbDeviceAlarmPageRequest request);

    void handle(GbDeviceAlarmHandleRequest request, String operator);

    void deleteAlarm(Long id);

    GbDeviceAlarm saveFromEvent(DeviceAlarm source);
}
