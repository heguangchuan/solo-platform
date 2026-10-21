package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbPtzPreset;

import java.util.List;

public interface GbPtzPresetService extends IService<GbPtzPreset> {

    List<GbPtzPreset> listByChannel(Long channelDbId);

    void savePreset(GbPtzPreset preset);

    void deletePreset(Long id);

    void call(Long id);
}
