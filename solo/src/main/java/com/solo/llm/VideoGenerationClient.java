package com.solo.llm;

import com.solo.entity.AiModelConfig;
import com.solo.request.VideoGenerationRequest;

/**
 * 视频生成客户端。
 */
public interface VideoGenerationClient {
    VideoGenerationSubmitResult submit(AiModelConfig cfg, VideoGenerationRequest request);

    VideoGenerationQueryResult query(AiModelConfig cfg, String taskId, String videoId);
}
