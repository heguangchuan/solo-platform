package com.solo.onvif.task;

import com.solo.onvif.entity.OnvifDevice;
import com.solo.onvif.mapper.OnvifDeviceMapper;
import com.solo.onvif.service.OnvifDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnvifDeviceStatusTask {

    private final OnvifDeviceMapper deviceMapper;
    private final OnvifDeviceService deviceService;
    private final ThreadPoolTaskExecutor taskExecutor;

    public void checkAll() {
        // 先查所有设备 id（mapper 轻量查询），再按 id 逐个查实体 + 探测（service 承载业务方法）
        List<OnvifDevice> all = deviceMapper.selectList(null);
        if (all.isEmpty()) {
            return;
        }
        log.debug("[ONVIF探测] 定时探测开始，共 {} 台", all.size());

        for (OnvifDevice device : all) {
            taskExecutor.execute(() -> {
                try {
                    deviceService.probeInternal(device);
                } catch (Exception e) {
                    log.warn("[ONVIF探测] 定时探测异常 id: {} 主机: {} 错误: {}",
                            device.getId(), device.getHost(), e.getMessage());
                }
            });
        }
    }
}
