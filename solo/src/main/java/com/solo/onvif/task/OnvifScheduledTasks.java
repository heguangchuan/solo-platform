package com.solo.onvif.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnvifScheduledTasks {

    private final OnvifDeviceStatusTask onvifDeviceStatusTask;

    @Scheduled(fixedDelay = 30_000L, initialDelay = 10_000L)
    public void checkAll() {
        onvifDeviceStatusTask.checkAll();
    }
}
