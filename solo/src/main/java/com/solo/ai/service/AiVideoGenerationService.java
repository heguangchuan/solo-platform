package com.solo.ai.service;

import com.solo.ai.request.VideoGenerationRequest;
import com.solo.ai.response.VideoGenerationResponse;

/**
 * 视频生成服务接口。
 */
public interface AiVideoGenerationService {
    VideoGenerationResponse generate(Long userId, VideoGenerationRequest request);

    void pollVideoProviderTasks();
}
