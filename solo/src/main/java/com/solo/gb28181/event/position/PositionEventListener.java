package com.solo.gb28181.event.position;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 设备定位监听器
 *
 * <h3>当前职责</h3>
 * <p>仅按统一格式日志输出定位内容，<b>不负责入库</b>。落库由并行订阅的
 * {@link PositionPersistenceListener} 完成；本类保留是为了运维 grep
 * 关键字 {@code [移动位置]} 即可定位实时上报。</p>
 *
 * <h3>线程</h3>
 * <p>挂 {@code @Async("gb28181BizExecutor")} 异步处理：SIP 监听线程解析完 XML 后立即
 * 继续处理下一条，定位日志落盘放到业务线程池。GPS 设备高频上报（典型 5s/次），
 * 必须异步避免日志量突增时阻塞 SIP 接收。</p>
 *
 * <h3>日志级别</h3>
 * <p>用 INFO 而非 WARN：定位是设备常态上报（不是异常事件），NVR / 车载 / 手持设备
 * 可能持续刷屏；与 {@link com.solo.gb28181.event.alarm.AlarmEventListener} 的
 * WARN 级（业务告警）区分。设备掉线 / 长时间无定位走 {@code @Scheduled} 巡检告警，
 * 不靠单条 INFO 捞。</p>
 *
 * <h3>扩展点</h3>
 * <p>要加落库 / 推 WebSocket / 写时序库等动作，<b>新写一个监听器</b>订阅 {@link PositionEvent} 即可，
 * 不要在本类里堆 if 分支——定位事件订阅者多对一干净，多种处理各起一个 listener。</p>
 */
@Slf4j
@Component
public class PositionEventListener {

    @Async("gb28181BizExecutor")
    @EventListener
    public void onPosition(PositionEvent event) {
        DevicePosition p = event.getPosition();
        log.info("[移动位置] deviceId={} time={} pos=({}, {}) speed={} direction={} altitude={}",
                p.getDeviceId(),
                p.getTime(),
                p.getLongitude(),
                p.getLatitude(),
                nullToEmpty(p.getSpeed()),
                nullToEmpty(p.getDirection()),
                nullToEmpty(p.getAltitude()));
    }

    private static String nullToEmpty(Double d) {
        return d == null ? "" : String.valueOf(d);
    }
}
