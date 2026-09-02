package com.solo.ai.request;

import lombok.Data;

import java.util.List;

/**
 * 视频生成请求体
 *
 * <p>对应 {@code POST /api/ai/task/video/generate}。支持场景：</p>
 * <ul>
 *   <li>文生视频：{@code mode=t2v}，只填 {@code prompt}</li>
 *   <li>图生视频：{@code mode=i2v}，再填 {@code image[]}（多张图按厂商约定拼成不同协议字段）</li>
 * </ul>
 *
 * <p>视频基本全是异步任务，提交后立即返回 taskId，{@code status} 多半是 {@code PENDING} / {@code PROCESSING}，
 * 需轮询 {@code GET /api/ai/task/{taskId}} 看进度。</p>
 */
@Data
public class VideoGenerationRequest {
    /** 可选；关联会话 id（用于把这次生成挂到某个创作会话的对话流里） */
    private Long conversationId;
    /** 可选；模型配置 id（{@code ai_model_config.id}），缺省走系统默认视频模型 */
    private Long modelConfigId;
    /** 必填；用户提示词 */
    private String prompt;
    /** 图生视频输入的图片 URL 列表（仅在 {@code mode=i2v} 时使用，每张需公网可访问） */
    private List<String> image;
    /** 可选；模式：{@code t2v} 文生视频 / {@code i2v} 图生视频。具体支持的取值因厂商而异 */
    private String mode;
    /** 可选；输出视频高度（像素）。具体支持值因厂商而异 */
    private Integer height;
    /** 可选；输出视频宽度（像素）。具体支持值因厂商而异 */
    private Integer width;
    /** 可选；视频总帧数。具体支持值因厂商而异 */
    private Integer numFrames;
    /** 可选；视频帧率（fps），典型值 24 / 30 */
    private Integer frameRate;
    /** 可选；推理步数，越大越慢但越精细；典型值 20~50 */
    private Integer numInferenceSteps;
    /** 可选；随机种子，固定后可复现结果 */
    private Integer seed;
    /** 可选；反向提示词，描述不希望出现的内容（如"模糊 / 畸变 / 水印"） */
    private String negativePrompt;
}
