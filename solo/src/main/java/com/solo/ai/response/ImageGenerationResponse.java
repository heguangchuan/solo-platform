package com.solo.ai.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 图片生成响应
 *
 * <p>对应 {@code POST /api/ai/task/image/generate}。每次请求会落 1 条任务 + N 张素材，
 * {@code N} 与请求体 {@code n} 字段一致；单图场景下 {@link #images} 长度为 1。</p>
 *
 * <h3>任务状态</h3>
 * <ul>
 *   <li>{@code PENDING} 灰色 — 已提交，等待调度</li>
 *   <li>{@code PROCESSING} 蓝色 — 厂商正在生成</li>
 *   <li>{@code SUCCESS} 绿色 — 成功，{@code images[].url} 可用</li>
 *   <li>{@code FAILED} 红色 — 失败，需查详情接口的 {@code errorMsg}</li>
 *   <li>{@code CANCELLED} 灰色 — 已取消</li>
 * </ul>
 */
@Schema(description = "图片生成响应")
@Data
public class ImageGenerationResponse {

    /** 关联会话 id（前端用来把图片挂回对话流） */
    @Schema(description = "关联会话 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 任务 id（用于后续轮询 {@code GET /api/ai/task/{taskId}}） */
    @Schema(description = "任务 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    /** 实际调用的模型名称（如 {@code dall-e-3} / {@code qwen-image-plus}） */
    @Schema(description = "实际调用的模型名称")
    private String modelName;
    /** 任务状态：{@code PENDING} / {@code PROCESSING} / {@code SUCCESS} / {@code FAILED} / {@code CANCELLED} */
    @Schema(description = "任务状态：PENDING / PROCESSING / SUCCESS / FAILED / CANCELLED")
    private String status;
    /** 生成的图片列表，{@code status=SUCCESS} 时有值；每张可直接显示在 UI 上 */
    @Schema(description = "生成的图片列表")
    private List<ImageItem> images;

    /**
     * 单张图片产物
     */
    @Schema(description = "单张图片产物")
    @Data
    public static class ImageItem {
        /** 素材 id（{@code ai_asset.id}），前端可拿这个 id 在素材库定位 */
        @Schema(description = "素材 ID")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long assetId;
        /** 访问 URL（可直接用于 {@code <img src="...">}） */
        @Schema(description = "访问 URL")
        private String url;
        /** 文件名（前端展示 / 下载用） */
        @Schema(description = "文件名")
        private String fileName;
    }
}
