package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDevicePageRequest;
import com.solo.gb28181.response.GbDeviceDetectResponse;
import com.solo.gb28181.response.GbDeviceStatusCountVO;

import java.util.List;

/**
 * <p>仅暴露"业务动作"。简单按字段查询（如按 deviceId 取一行）由调用方直接走
 * {@code lambdaQuery().eq(GbDevice::getDeviceId, ...).one()}，不在此处增加薄方法。</p>
 */
public interface GbDeviceService extends IService<GbDevice> {

    Page<GbDevice> pageQuery(GbDevicePageRequest request);

    List<GbDeviceStatusCountVO> statusCount();

    void createByManual(GbDevice device, java.util.List<GbDeviceChannel> channels);

    void updateByManual(GbDevice device);

    void online(GbDevice device);

    void offline(String deviceId, String reason);

    void keepalive(GbDevice device);

    void deleteBatch(java.util.List<Long> ids);

    void evictProfileCache(String deviceId);

    GbDeviceDetectResponse detectBatch(java.util.List<Long> ids);

    Page<GbDevice> searchByKeyword(String keyword, Long page, Long pageSize);
}
