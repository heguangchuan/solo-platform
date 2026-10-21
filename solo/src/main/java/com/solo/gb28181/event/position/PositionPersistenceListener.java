package com.solo.gb28181.event.position;

import com.solo.gb28181.service.GbMobilePositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * <p>与只打日志的 {@link PositionEventListener} 并行订阅 {@link PositionEvent}，互不影响：
 * 本监听器负责把轨迹点写入 {@code gb_mobile_position}（历史轨迹数据源）。
 * 异步执行，不阻塞 SIP 报文处理线程。定位高频上报但不做实时推送，故不广播 WebSocket。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionPersistenceListener {

    private final GbMobilePositionService positionService;

    @Async("gb28181BizExecutor")
    @EventListener
    public void onPosition(PositionEvent event) {
        DevicePosition source = event.getPosition();
        try {
            positionService.saveFromEvent(source);
        } catch (Exception e) {
            // 落库失败不能影响 SIP 主链路与其它监听器
            log.error("[移动位置] 轨迹落库失败 deviceId={} time={}",
                    source.getDeviceId(), source.getTime(), e);
        }
    }
}
