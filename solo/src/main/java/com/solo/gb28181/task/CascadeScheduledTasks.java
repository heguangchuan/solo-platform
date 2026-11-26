package com.solo.gb28181.task;

import com.solo.gb28181.service.PlatformRegisterService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CascadeScheduledTasks {

    private final PlatformRegisterService platformRegisterService;

    @Scheduled(initialDelay = 10_000, fixedDelay = Long.MAX_VALUE)
    public void cascadeRegisterOnStartup() {
        platformRegisterService.registerAll();
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void cascadeKeepalive() {
        platformRegisterService.keepaliveAll();
        platformRegisterService.registerOffline();
    }

    @Scheduled(initialDelay = 1_800_000, fixedDelay = 1_800_000)
    public void cascadeRefresh() {
        platformRegisterService.refreshAll();
    }

    @PreDestroy
    public void cascadeUnregister() {
        try {
            platformRegisterService.unregisterAll();
        } catch (Exception e) {
            log.warn("[级联注册] 关停注销异常: {}", e.getMessage());
        }
    }
}
