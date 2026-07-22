package com.solo.onvif.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.request.OnvifDevicePageRequest;

/**
 * ONVIF 设备服务
 *
 * <p>与国标 {@code GbDeviceService} 一样，仅暴露"业务动作"。简单的按字段查询走
 * {@code lambdaQuery()} 内联即可。</p>
 */
public interface OnvifDeviceService extends IService<OnvifDevice> {

    Page<OnvifDevice> pageQuery(OnvifDevicePageRequest request);

    /**
     * 保存或更新设备。保存成功后同步探测一次，回填 manufacturer / model / firmware / profile / stream_uri / status
     */
    void saveWithProbe(OnvifDevice device);

    /**
     * 主动探测一次（前端"探测/刷新"按钮）
     */
    void probe(Long id);

    void probeInternal(OnvifDevice device);
}
