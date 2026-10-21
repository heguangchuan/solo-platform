package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.response.GbChannelStatusCountVO;
import com.solo.gb28181.response.GbDeviceChannelResponse;

import java.util.List;

/**
 * <p>简单按字段查询直接走 {@code lambdaQuery()}，本接口只保留"业务动作"。</p>
 */
public interface GbDeviceChannelService extends IService<GbDeviceChannel> {

    Page<GbDeviceChannelResponse> pageQuery(GbDeviceChannelPageRequest request);

    List<GbChannelStatusCountVO> statusCount();

    void createByManual(GbDeviceChannel channel);

    void updateByManual(GbDeviceChannel channel);

    void deleteById(Long id);

    void saveOrUpdateBatch(List<GbDeviceChannel> channels);

    void deleteByDeviceId(String deviceId);

    void deleteBatch(List<Long> ids);

}
