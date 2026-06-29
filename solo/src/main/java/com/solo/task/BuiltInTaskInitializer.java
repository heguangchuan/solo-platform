package com.solo.task;

import com.solo.common.SystemConstants;
import com.solo.entity.SysTask;
import com.solo.service.SysTaskService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * 系统内置动态任务初始化。
 */
@Slf4j
@Component
@DependsOn("dynamicTaskScheduler")
@RequiredArgsConstructor
public class BuiltInTaskInitializer {

    private final SysTaskService sysTaskService;
    private final DynamicTaskScheduler dynamicTaskScheduler;

    @PostConstruct
    public void init() {
        ensureAiVideoProviderPollTask();
    }

    private void ensureAiVideoProviderPollTask() {
        String taskCode = "ai.video.provider.poll";
        SysTask task = sysTaskService.lambdaQuery()
                .eq(SysTask::getTaskCode, taskCode)
                .one();
        if (task == null) {
            task = new SysTask();
            task.setTaskName("AI视频供应商任务轮询");
            task.setTaskCode(taskCode);
            task.setCron("0/30 * * * * ?");
            task.setHandlerName("aiVideoProviderTaskPoll");
            task.setTaskParams("{}");
            task.setStatus(SystemConstants.STATUS_ENABLED);
            task.setRemark("轮询 Agnes 视频生成任务状态");
            sysTaskService.save(task);
            log.info("初始化内置定时任务：{}", taskCode);
        }
        if (task.getStatus() != null && task.getStatus() == SystemConstants.STATUS_ENABLED) {
            dynamicTaskScheduler.schedule(task);
        }
    }
}
