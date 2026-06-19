package com.solo.request;

import lombok.Data;

/**
 * AI素材新增/编辑请求体
 */
@Data
public class AiAssetRequest {
    private Long userId;
    private Long taskId;
    private String assetType;
    private String fileName;
    private String fileUrl;
    private String coverUrl;
    private String thumbnailUrl;
    private String mimeType;
    private String ext;
    private Long size;
    private Integer duration;
    private Integer width;
    private Integer height;
    private String prompt;
    private String modelName;
    private Integer status;
    private String remark;
}
