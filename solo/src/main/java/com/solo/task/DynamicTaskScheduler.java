package com.solo.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.common.SystemConstants;
import com.solo.entity.SysTask;
import com.solo.entity.SysTaskLog;
import com.solo.service.SysTaskLogService;
import com.solo.service.SysTaskService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicTaskScheduler {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final SysTaskService sysTaskService;
    private final SysTaskLogService sysTaskLogService;
    private final TaskHandlerRegistry taskHandlerRegistry;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        sysTaskService.list(new LambdaQueryWrapper<SysTask>()
                        .eq(SysTask::getStatus, SystemConstants.STATUS_ENABLED))
                .forEach(task -> {
                    try {
                        schedule(task);
                    } catch (Exception e) {
                        log.warn("启动定时任务失败: taskCode={}, error={}", task.getTaskCode(), e.getMessage());
                    }
                });
    }

    public void schedule(SysTask task) {
        stop(task.getId());
        CronTrigger trigger = new CronTrigger(task.getCron(), TimeZone.getDefault());
        ScheduledFuture<?> future = taskScheduler.schedule(() -> execute(task.getId(), true), trigger);
        if (future != null) {
            scheduledTasks.put(task.getId(), future);
        }
    }

    public void stop(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void runOnce(Long taskId) {
        taskScheduler.execute(() -> execute(taskId, false));
    }

    public boolean isScheduled(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        return future != null && !future.isCancelled();
    }

    private void execute(Long taskId, boolean requireEnabled) {
        SysTask task = sysTaskService.getById(taskId);
        if (task == null) {
            stop(taskId);
            return;
        }
        if (requireEnabled && task.getStatus() != SystemConstants.STATUS_ENABLED) {
            stop(taskId);
            return;
        }
        TaskHandler handler = taskHandlerRegistry.getHandler(task.getHandlerName());
        if (handler == null) {
            saveLog(task, 0L, SystemConstants.STATUS_DISABLED, "任务处理器不存在: " + task.getHandlerName());
            return;
        }

        long start = System.currentTimeMillis();
        try {
            handler.execute(task.getTaskParams());
            saveLog(task, System.currentTimeMillis() - start, SystemConstants.STATUS_ENABLED, null);
        } catch (Exception e) {
            log.warn("定时任务执行失败: taskCode={}, error={}", task.getTaskCode(), e.getMessage(), e);
            saveLog(task, System.currentTimeMillis() - start, SystemConstants.STATUS_DISABLED, e.getMessage());
        }
    }

    private void saveLog(SysTask task, Long durationMs, Integer resultStatus, String errorMsg) {
        SysTaskLog taskLog = new SysTaskLog();
        taskLog.setTaskId(task.getId());
        taskLog.setTaskName(task.getTaskName());
        taskLog.setTaskCode(task.getTaskCode());
        taskLog.setHandlerName(task.getHandlerName());
        taskLog.setTaskParams(task.getTaskParams());
        taskLog.setExecutionTime(LocalDateTime.now());
        taskLog.setDurationMs(durationMs);
        taskLog.setResultStatus(resultStatus);
        taskLog.setErrorMsg(errorMsg == null ? null : errorMsg.substring(0, Math.min(errorMsg.length(), 2000)));
        sysTaskLogService.save(taskLog);
    }
}
