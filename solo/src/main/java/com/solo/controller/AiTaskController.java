package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.entity.AiGenerationTask;
import com.solo.service.AiGenerationTaskService;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI创作任务控制器
 */
@RestController
@RequestMapping("/api/ai/task")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiGenerationTaskService generationTaskService;

    /**
     * 获取用户创作任务列表
     */
    @GetMapping("/list")
    @RequirePermission({"ai:history:list"})
    public Result<PageResult<AiGenerationTask>> getTaskList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status) {
        Long userId = UserHolderUtils.getCurrentUserId();
        Page<AiGenerationTask> pageResult = generationTaskService.getUserTasks(userId, taskType, status, page, pageSize);
        return Result.success(new PageResult<>(pageResult.getTotal(), pageResult.getRecords()));
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    @RequirePermission({"ai:history:detail"})
    public Result<AiGenerationTask> getTaskDetail(@PathVariable Long taskId) {
        AiGenerationTask task = generationTaskService.getById(taskId);
        return Result.success(task);
    }
}
