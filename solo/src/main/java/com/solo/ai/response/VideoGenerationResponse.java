package com.solo.ai.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 视频生成响应
 *
 * <p>对应 {@code POST /api/ai/task/video/generate}。视频基本都是异步任务：</p>
 * <ul>
 *   <li>返回时 {@link #status} 多半是 {@code PENDING}（已提交等待调度）或 {@code PROCESSING}（厂商正在生成）</li>
 *   <li>前端拿 {@link #taskId} 后，每 3~5 秒调 {@code GET /api/ai/task/{taskId}} 轮询</li>
 *   <li>成功后 {@link #videoUrl} / {@link #assetId} / {@link #fileName} 才有值</li>
 * </ul>
 *
 * <p>极少数厂商支持同步返回：调用结束后 {@link #status} 已经是 {@code SUCCESS}，{@link #videoUrl} 可直接播放。</p>
 */
@Schema(description = "视频生成响应")
@Data
public class VideoGenerationResponse {
    /** 关联会话 id（前端用来把视频挂回对话流） */
    @Schema(description = "关联会话 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 任务 id（用于后续轮询 / 详情查询） */
    @Schema(description = "任务 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 实际调用的模型名称（如 {@code kling-1.6} / {@code cogvideox-5b}） */
    @Schema(description = "实际调用的模型名称")
    private String modelName;
    /** 任务状态：{@code PENDING} 灰色 / {@code PROCESSING} 蓝色 / {@code SUCCESS} 绿色 / {@code FAILED} 红色 / {@code CANCELLED} 灰色 */
    @Schema(description = "任务状态：PENDING 灰色 / PROCESSING 蓝色 / SUCCESS 绿色 / FAILED 红色 / CANCELLED 灰色")
    private String status;

    /** 关联素材 id（{@code ai_asset.id}，{@code status=SUCCESS} 后才有值） */
    @Schema(description = "关联素材 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assetId;

    /** 视频访问 URL（{@code status=SUCCESS} 后才有值，可直接用于 {@code <video src="...">}） */
    @Schema(description = "视频访问 URL")
    private String videoUrl;
    /** 文件名（{@code status=SUCCESS} 后才有值，前端展示 / 下载用） */
    @Schema(description = "文件名")
    private String fileName;
}
