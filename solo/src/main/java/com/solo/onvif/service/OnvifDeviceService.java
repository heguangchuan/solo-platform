package com.solo.onvif.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.request.OnvifDevicePageRequest;

/**
 * <p>与国标 {@code GbDeviceService} 一样，仅暴露"业务动作"。简单的按字段查询走
 * {@code lambdaQuery()} 内联即可。</p>
 */
public interface OnvifDeviceService extends IService<OnvifDevice> {

    Page<OnvifDevice> pageQuery(OnvifDevicePageRequest request);

    void saveWithProbe(OnvifDevice device);

    void probe(Long id);

    void probeInternal(OnvifDevice device);
}
