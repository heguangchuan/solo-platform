package com.solo.ai.request;

import lombok.Data;

/**
 * AI素材新增/编辑请求体
 */
@Data
public class AiAssetRequest {
    private String assetType;
    private String source;
    private String fileName;
    private String fileUrl;
    private String coverUrl;
}
