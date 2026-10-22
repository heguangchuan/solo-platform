package com.solo.gb28181.task;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.service.GbRecordFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 云端录像定时任务（聚合器风格）：上传重试、过期清理，以及录像计划对账委托。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudRecordScheduledTasks {

    private final GbRecordFileService recordFileService;
    private final Gb28181Properties gb28181Properties;
    private final RecordPlanReconciler recordPlanReconciler;

    /** 云端录像切片上传重试：每 60s 重领 FAILED（未超重试上限）和僵死的 UPLOADING 切片 */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void retryRecordUpload() {
        int picked = recordFileService.retryFailed();
        if (picked > 0) {
            log.info("[云端录像] 重领上传任务 {} 个", picked);
        }
    }

    /** 云端录像过期清理：每天凌晨 04:35 按保留天数删存储对象 + 元数据（retentionDays<=0 不清理） */
    @Scheduled(cron = "0 35 4 * * ?", zone = "Asia/Shanghai")
    public void cleanExpiredRecords() {
        int retentionDays = gb28181Properties.getRecord().getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        int deleted = recordFileService.cleanExpired(retentionDays);
        log.info("[云端录像] 过期录像清理完成 retentionDays={} 删除条数={}", retentionDays, deleted);
    }

    /** 录像计划对账：每 60s 把"该录的计划"和"实际 REC 会话"收敛一次（业务在 RecordPlanReconciler） */
    @Scheduled(initialDelay = 20_000, fixedDelay = 60_000)
    public void reconcileRecordPlan() {
        try {
            recordPlanReconciler.reconcile();
        } catch (Exception e) {
            log.error("[录像计划对账] 调度异常: {}", e.getMessage(), e);
        }
    }
}
