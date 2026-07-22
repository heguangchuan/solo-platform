package com.solo.ai.service;

import com.solo.ai.request.ImageGenerationRequest;
import com.solo.ai.response.ImageGenerationResponse;

/**
 * 图片生成服务接口。
 */
public interface AiImageGenerationService {

    ImageGenerationResponse generate(Long userId, ImageGenerationRequest request);
}
