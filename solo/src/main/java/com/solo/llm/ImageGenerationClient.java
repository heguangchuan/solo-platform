package com.solo.llm;

import com.solo.entity.AiModelConfig;
import com.solo.request.ImageGenerationRequest;

/**
 * 图片生成客户端。
 */
public interface ImageGenerationClient {

    ImageGenerationResult generate(AiModelConfig cfg, ImageGenerationRequest request);
}
