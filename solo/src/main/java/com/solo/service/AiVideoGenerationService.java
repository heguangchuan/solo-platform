package com.solo.service;

import com.solo.request.VideoGenerationRequest;
import com.solo.response.VideoGenerationResponse;

/**
 * 视频生成服务接口。
 */
public interface AiVideoGenerationService {
    VideoGenerationResponse generate(Long userId, VideoGenerationRequest request);

    void pollVideoProviderTasks();
}
