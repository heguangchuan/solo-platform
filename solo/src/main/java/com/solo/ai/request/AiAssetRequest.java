package com.solo.ai.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 素材新增 / 编辑请求体
 *
 * <p>对应 {@code POST /api/ai/asset} 与 {@code PUT /api/ai/asset/{id}}。
 * 文件本体（图片 / 视频 / 音频二进制）走 MinIO，本接口只存元数据和访问 URL。</p>
 */
@Schema(description = "AI 素材新增 / 编辑请求体")
@Data
public class AiAssetRequest {
    /** 必填；素材类型：{@code IMAGE} 图片 / {@code VIDEO} 视频 / {@code AUDIO} 音频 / {@code DOCUMENT} 文档 */
    @Schema(description = "素材类型：IMAGE 图片 / VIDEO 视频 / AUDIO 音频 / DOCUMENT 文档")
    private String assetType;
    /** 必填；来源：{@code SYSTEM_UPLOAD} 系统上传 / {@code MODEL_GENERATE} 模型生成 */
    @Schema(description = "来源：SYSTEM_UPLOAD 系统上传 / MODEL_GENERATE 模型生成")
    private String source;
    /** 必填；文件名（前端展示用） */
    @Schema(description = "文件名（前端展示用）")
    private String fileName;
    /** 必填；文件访问 URL（MinIO 返回的预签名地址，可直接用于 {@code <img>} / {@code <video>} / {@code <audio>}） */
    @Schema(description = "文件访问 URL（MinIO 返回的预签名地址）")
    private String fileUrl;
    /** 可选；封面图 URL（视频素材建议必填，其他可空），用于缩略图预览 */
    @Schema(description = "封面图 URL（视频素材建议必填，其他可空）")
    private String coverUrl;
}
