package com.solo.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.ai.entity.AiGenerationTask;
import com.solo.ai.request.ImageGenerationRequest;
import com.solo.ai.request.VideoGenerationRequest;
import com.solo.ai.response.ImageGenerationResponse;
import com.solo.ai.response.VideoGenerationResponse;
import com.solo.ai.service.AiGenerationTaskService;
import com.solo.ai.service.AiImageGenerationService;
import com.solo.ai.service.AiVideoGenerationService;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI 创作任务管理
 */
@RestController
@RequestMapping("/api/ai/task")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiGenerationTaskService generationTaskService;
    private final AiImageGenerationService imageGenerationService;
    private final AiVideoGenerationService videoGenerationService;

    /**
     * 提交图片生成任务
     */
    @PostMapping("/image/generate")
    @RequirePermission({"ai:imageCreation:generate"})
    public Result<ImageGenerationResponse> generateImage(@RequestBody ImageGenerationRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();
        return Result.success(imageGenerationService.generate(userId, request));
    }

    /**
     * 提交视频生成任务
     */
    @PostMapping("/video/generate")
    @RequirePermission({"ai:videoCreation:generate"})
    public Result<VideoGenerationResponse> generateVideo(@RequestBody VideoGenerationRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();
        return Result.success(videoGenerationService.generate(userId, request));
    }

    /**
     * 获取当前用户的创作任务列表
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param taskType 可选过滤；`TEXT` / `IMAGE` / `VIDEO` / `AUDIO`
     * @param status 可选过滤；`PENDING` / `PROCESSING` / `SUCCESS` / `FAILED` / `CANCELLED`
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
     * 任务详情
     *
     * @param taskId 任务主键 ID
     */
    @GetMapping("/{taskId}")
    @RequirePermission({"ai:history:detail"})
    public Result<AiGenerationTask> getTaskDetail(@PathVariable Long taskId) {
        AiGenerationTask task = generationTaskService.getById(taskId);
        return Result.success(task);
    }
}
