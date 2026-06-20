package com.solo.service;

import com.solo.request.ImageGenerationRequest;
import com.solo.response.ImageGenerationResponse;

/**
 * 图片生成服务接口。
 */
public interface AiImageGenerationService {

    ImageGenerationResponse generate(Long userId, ImageGenerationRequest request);
}
