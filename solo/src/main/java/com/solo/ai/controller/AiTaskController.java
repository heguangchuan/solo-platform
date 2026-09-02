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
 * AI 创作任务（图片 / 视频异步生成）
 *
 * <p>前端"AI 创作"页的"历史记录"功能用这套接口：每次发起的生图 / 生视频都会落 1 条任务，
 * 通过轮询查进度，结果会回填到任务表。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/ai/task/image/generate} — 提交图片生成任务，立即返回 taskId / 状态</li>
 *   <li>{@code POST /api/ai/task/video/generate} — 提交视频生成任务，立即返回 taskId / 状态</li>
 *   <li>{@code GET /api/ai/task/list} — 当前登录用户的创作任务列表（按 taskType / status 过滤，分页）</li>
 *   <li>{@code GET /api/ai/task/{taskId}} — 任务详情</li>
 * </ul>
 *
 * <h3>同步 vs 异步</h3>
 * <p>部分图片模型（OpenAI gpt-image-1 / 通义等）支持同步返回，调用结束后 {@code status} 已经是 {@code SUCCESS}，
 * 响应里的 {@code images[].url} 直接可用。视频模型基本都是异步的（需 30s ~ 几分钟），需要轮询。</p>
 *
 * <h3>任务状态机</h3>
 * <ul>
 *   <li>{@code PENDING} 灰色 — 已提交，等待调度（视频场景最常见）</li>
 *   <li>{@code PROCESSING} 蓝色 — 厂商正在生成</li>
 *   <li>{@code SUCCESS} 绿色 — 成功；{@code images[].url} / {@code videoUrl} 字段有值</li>
 *   <li>{@code FAILED} 红色 — 失败；{@code errorMsg} 字段有值，前端悬浮展示</li>
 *   <li>{@code CANCELLED} 灰色 — 已取消</li>
 * </ul>
 *
 * <p>轮询建议：每 3~5 秒调一次 {@code /list} 或 {@code /{taskId}}，发现状态到终态就停。</p>
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
     *
     * <p>请求体见 {@link ImageGenerationRequest}。响应见 {@link ImageGenerationResponse}：</p>
     * <ul>
     *   <li>同步模型（如 OpenAI gpt-image-1）通常直接返回 {@code status=SUCCESS}，{@code images[].url} 可用</li>
     *   <li>异步模型可能返回 {@code status=PENDING}，需要走 {@code /list} 轮询</li>
     * </ul>
     *
     * <p>支持：纯文本生图（{@code prompt} 必填）、图生图 / 局部编辑（{@code image[]} 传参考图）。</p>
     */
    @PostMapping("/image/generate")
    @RequirePermission({"ai:imageCreation:generate"})
    public Result<ImageGenerationResponse> generateImage(@RequestBody ImageGenerationRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();
        return Result.success(imageGenerationService.generate(userId, request));
    }

    /**
     * 提交视频生成任务
     *
     * <p>请求体见 {@link VideoGenerationRequest}。响应见 {@link VideoGenerationResponse}。</p>
     *
     * <p>视频基本都是异步任务：返回时 {@code status} 通常是 {@code PENDING} / {@code PROCESSING}，
     * 后台定时轮询把厂商结果回填；前端每 3~5 秒调一次 {@code /list} 或 {@code /{taskId}} 查进度。
     * 成功后在响应 / 详情里能拿到 {@code videoUrl}（可直接放到 {@code <video>} 标签的 src）。</p>
     *
     * <p>支持：文生视频（{@code mode=t2v}）、图生视频（{@code mode=i2v} + {@code image[]}）。</p>
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
     * <p>URL 参数：
     * <ul>
     *   <li>{@code page} — 页码，从 1 开始，默认 1</li>
     *   <li>{@code pageSize} — 每页条数，默认 20</li>
     *   <li>{@code taskType} — 可选过滤；{@code TEXT} / {@code IMAGE} / {@code VIDEO} / {@code AUDIO}</li>
     *   <li>{@code status} — 可选过滤；{@code PENDING} / {@code PROCESSING} / {@code SUCCESS} / {@code FAILED} / {@code CANCELLED}</li>
     * </ul>
     * 只返回当前登录用户自己的任务，按提交时间倒序。</p>
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
     * <p>id 不存在时返回 {@code null}，由前端判断空值给"任务已删除"提示。
     * 任务本身归属在 {@code userId} 字段上，跨用户访问不会拦截（仅限管理员场景下能查别人任务）。</p>
     */
    @GetMapping("/{taskId}")
    @RequirePermission({"ai:history:detail"})
    public Result<AiGenerationTask> getTaskDetail(@PathVariable Long taskId) {
        AiGenerationTask task = generationTaskService.getById(taskId);
        return Result.success(task);
    }
}
