package com.solo.request;

import lombok.Data;

import java.util.List;

/**
 * 视频生成请求体。
 */
@Data
public class VideoGenerationRequest {
    private Long conversationId;
    private Long modelConfigId;
    private String prompt;
    private List<String> image;
    private String mode;
    private Integer height;
    private Integer width;
    private Integer numFrames;
    private Integer frameRate;
    private Integer numInferenceSteps;
    private Integer seed;
    private String negativePrompt;
}
