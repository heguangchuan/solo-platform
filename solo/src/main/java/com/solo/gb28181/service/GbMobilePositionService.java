package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbMobilePosition;
import com.solo.gb28181.event.position.DevicePosition;
import com.solo.gb28181.request.GbMobilePositionPageRequest;

import java.util.List;

/**
 * 设备移动位置（GPS 轨迹）服务
 */
public interface GbMobilePositionService extends IService<GbMobilePosition> {

    /**
     * 分页查询轨迹点
     */
    Page<GbMobilePosition> pageQuery(GbMobilePositionPageRequest request);

    /**
     * 按设备 + 时间段查询有序轨迹点（时间升序），最多返回上限条数
     */
    List<GbMobilePosition> track(GbMobilePositionPageRequest request);

    /**
     * 删除单个轨迹点
     */
    void deletePosition(Long id);

    /**
     * 将 SIP 事件层 DTO 落库（含设备名称冗余）
     *
     * @return 落库后的轨迹点实体
     */
    GbMobilePosition saveFromEvent(DevicePosition source);

    /**
     * 清理过期轨迹点（按入库时间）
     *
     * @param retentionDays 保留天数
     * @return 删除条数
     */
    int cleanExpired(int retentionDays);
}
