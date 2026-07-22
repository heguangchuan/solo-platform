package com.solo.ai.llm;

import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.request.VideoGenerationRequest;

/**
 * 视频生成客户端。
 */
public interface VideoGenerationClient {
    VideoGenerationSubmitResult submit(AiModelConfig cfg, VideoGenerationRequest request);

    VideoGenerationQueryResult query(AiModelConfig cfg, String taskId, String videoId);
}
