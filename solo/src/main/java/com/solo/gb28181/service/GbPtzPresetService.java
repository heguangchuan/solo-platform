package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbPtzPreset;

import java.util.List;

/**
 * 云台预置位服务
 */
public interface GbPtzPresetService extends IService<GbPtzPreset> {

    /**
     * 查询通道所有预置位
     */
    List<GbPtzPreset> listByChannel(Long channelDbId);

    /**
     * 新增预置位（自动计算编号，同时通知设备保存当前位置）
     */
    void savePreset(GbPtzPreset preset);

    /**
     * 修改预置位名称（仅改数据库，无需通知设备）
     */
    void updatePreset(GbPtzPreset preset);

    /**
     * 删除预置位（同时通知设备删除）
     */
    void deletePreset(Long id);

    /**
     * 调用预置位
     */
    void call(Long id);
}
