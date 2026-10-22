package com.solo.gb28181.task;

import com.solo.gb28181.service.PlatformRegisterService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 级联（上级平台）注册生命周期定时任务（聚合器风格）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CascadeScheduledTasks {

    private final PlatformRegisterService platformRegisterService;

    /** 启动后 10s 对所有启用的上级平台发起首次注册（仅执行一次） */
    @Scheduled(initialDelay = 10_000, fixedDelay = Long.MAX_VALUE)
    public void cascadeRegisterOnStartup() {
        platformRegisterService.registerAll();
    }

    /** 级联心跳 + 离线重注册：每 60s 一次（在线发心跳，离线重试注册） */
    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void cascadeKeepalive() {
        platformRegisterService.keepaliveAll();
        platformRegisterService.registerOffline();
    }

    /** 级联注册刷新：每 30min 重发一次注册，防止 expires 过期 */
    @Scheduled(initialDelay = 1_800_000, fixedDelay = 1_800_000)
    public void cascadeRefresh() {
        platformRegisterService.refreshAll();
    }

    /** 关停时对所有在线上级发送注销 */
    @PreDestroy
    public void cascadeUnregister() {
        try {
            platformRegisterService.unregisterAll();
        } catch (Exception e) {
            log.warn("[级联注册] 关停注销异常: {}", e.getMessage());
        }
    }
}
