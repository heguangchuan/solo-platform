package com.solo.media.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 流媒体模块定时任务统一入口
 * <p>与 {@code com.solo.gb28181.task.GbScheduledTasks} 风格保持一致：
 * 业务类只保留纯业务方法（{@link MediaServerHealthDetector#probe()}），本类持有
 * {@link Scheduled} 触发点后逐个委派，方便统一管理调度周期。</p>
 *
 * <ul>
 *   <li>启动后 5s 起、之后每 10s → {@link MediaServerHealthDetector#probe()} ZLM 节点健康探测</li>
 * </ul>
 *
 * <p>调度总开关 {@code @EnableScheduling} 在 {@code SoloApplication} 上开启。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaScheduledTasks {

    private final MediaServerHealthDetector healthDetector;

    /**
     * ZLM 节点健康探测：每 10s 主动 ping 一轮所有登记节点，
     * 把掉线的 status 拉回 0、补齐 push-only 模型的盲区。
     * 初始延迟 5s 避开应用启动期，等 MediaServerBootstrap 完成自举后再开始探。
     */
    @Scheduled(initialDelay = 5_000, fixedDelay = 10_000)
    public void probeMediaServers() {
        try {
            healthDetector.probe();
        } catch (Exception e) {
            // 探测是 best-effort，任何异常都不能拖垮调度线程
            log.warn("[流媒体定时] 健康探测异常 {}", e.getMessage(), e);
        }
    }
}
