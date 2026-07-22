package com.solo.ai.llm;

import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.request.ImageGenerationRequest;

/**
 * 图片生成客户端。
 */
public interface ImageGenerationClient {

    ImageGenerationResult generate(AiModelConfig cfg, ImageGenerationRequest request);
}
