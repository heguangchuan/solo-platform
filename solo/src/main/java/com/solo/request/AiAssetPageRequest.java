package com.solo.request;

import lombok.Data;

/**
 * AI素材分页查询请求参数
 */
@Data
public class AiAssetPageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;
    private String fileName;
    private String assetType;
    private String source;
}
