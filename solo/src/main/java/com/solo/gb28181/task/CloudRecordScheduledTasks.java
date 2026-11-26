package com.solo.gb28181.task;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.service.GbRecordFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudRecordScheduledTasks {

    private final GbRecordFileService recordFileService;
    private final Gb28181Properties gb28181Properties;

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void retryRecordUpload() {
        int picked = recordFileService.retryFailed();
        if (picked > 0) {
            log.info("[云端录像] 重领上传任务 {} 个", picked);
        }
    }

    @Scheduled(cron = "0 35 4 * * ?", zone = "Asia/Shanghai")
    public void cleanExpiredRecords() {
        int retentionDays = gb28181Properties.getRecord().getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        int deleted = recordFileService.cleanExpired(retentionDays);
        log.info("[云端录像] 过期录像清理完成 retentionDays={} 删除条数={}", retentionDays, deleted);
    }
}
