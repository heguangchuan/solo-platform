package com.solo.llm;

import com.solo.llm.client.AgnesVideoClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 视频生成客户端注册器。
 */
@Component
@RequiredArgsConstructor
public class VideoGenerationClientRegistry {
    private final AgnesVideoClient agnesVideoClient;

    public VideoGenerationClient resolve(String provider) {
        return agnesVideoClient;
    }
}
