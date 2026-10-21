package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbMobilePosition;
import com.solo.gb28181.event.position.DevicePosition;
import com.solo.gb28181.request.GbMobilePositionPageRequest;

import java.util.List;

public interface GbMobilePositionService extends IService<GbMobilePosition> {

    Page<GbMobilePosition> pageQuery(GbMobilePositionPageRequest request);

    List<GbMobilePosition> track(GbMobilePositionPageRequest request);

    void deletePosition(Long id);

    GbMobilePosition saveFromEvent(DevicePosition source);

    int cleanExpired(int retentionDays);
}
