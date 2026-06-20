package com.solo.request;

import lombok.Data;

import java.util.List;

/**
 * 图片生成请求体。
 */
@Data
public class ImageGenerationRequest {
    private Long conversationId;
    private Long modelConfigId;
    private String prompt;
    private String size;
    private Integer n;
    private String responseFormat;
    private List<String> image;
}
