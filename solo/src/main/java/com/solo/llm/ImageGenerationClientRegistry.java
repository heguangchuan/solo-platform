package com.solo.llm;

import com.solo.llm.client.OpenAiImageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 图片生成客户端注册器。
 */
@Component
@RequiredArgsConstructor
public class ImageGenerationClientRegistry {

    private final OpenAiImageClient openAiImageClient;

    public ImageGenerationClient resolve(String provider) {
        return openAiImageClient;
    }
}
