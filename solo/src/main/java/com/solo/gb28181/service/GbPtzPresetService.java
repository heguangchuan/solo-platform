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
     * 调用预置位
     */
    void call(Long id);
}
