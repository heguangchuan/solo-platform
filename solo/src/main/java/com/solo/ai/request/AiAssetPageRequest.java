package com.solo.ai.request;

import lombok.Data;

/**
 * AI 素材分页查询请求参数
 *
 * <p>对应 {@code POST /api/ai/asset/page}。所有字段都可选；前端在素材管理页 / 创作页选素材弹窗里用。</p>
 */
@Data
public class AiAssetPageRequest {
    /** 当前页码，从 1 开始，默认 1 */
    private Long page = 1L;
    /** 每页条数，默认 20 */
    private Long pageSize = 20L;
    /** 按文件名模糊过滤（可选） */
    private String fileName;
    /** 按素材类型过滤（可选）：{@code IMAGE} 图片 / {@code VIDEO} 视频 / {@code AUDIO} 音频 / {@code DOCUMENT} 文档 */
    private String assetType;
    /** 按来源过滤（可选）：{@code SYSTEM_UPLOAD} 系统上传 / {@code MODEL_GENERATE} 模型生成 */
    private String source;
}
