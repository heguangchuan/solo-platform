package com.solo.ai.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.solo.common.ServiceException;
import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.llm.AttachmentFetcher;
import com.solo.ai.llm.HttpClientHolder;
import com.solo.ai.llm.ImageGenerationClient;
import com.solo.ai.llm.ImageGenerationResult;
import com.solo.ai.request.ImageGenerationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容图片生成客户端。
 */
@Component
@Slf4j
public class OpenAiImageClient implements ImageGenerationClient {

    @Override
    public ImageGenerationResult generate(AiModelConfig cfg, ImageGenerationRequest request) {
        Map<String, Object> body = buildBody(cfg, request);
        HttpRequest httpRequest = buildRequest(cfg, body);
        try {
            HttpResponse<String> response = HttpClientHolder.HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String summary = summarizeError(response.body());
                log.warn("图片生成调用失败：status={}, body={}", response.statusCode(), response.body());
                throw new ServiceException("图片生成失败：HTTP " + response.statusCode()
                        + (summary.isEmpty() ? "" : " - " + summary));
            }
            List<String> urls = parseImages(response.body());
            if (urls.isEmpty()) {
                throw new ServiceException("图片生成响应格式异常");
            }
            return new ImageGenerationResult(urls);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("图片生成调用异常：{}", e.getMessage(), e);
            throw new ServiceException("图片生成调用异常", e);
        }
    }

    private Map<String, Object> buildBody(AiModelConfig cfg, ImageGenerationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModelName());
        body.put("prompt", request.getPrompt());
        applyModelParams(body, cfg.getModelParams());
        if (request.getSize() != null && !request.getSize().isBlank()) {
            body.put("size", request.getSize());
        }
        if (request.getImage() != null && !request.getImage().isEmpty()) {
            List<String> images = request.getImage().stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(item -> AttachmentFetcher.toDataUri(item, null))
                    .filter(item -> item != null && !item.isBlank())
                    .toList();
            if (images.isEmpty()) {
                throw new ServiceException("参考图片读取失败，请检查图片是否可访问");
            }
            putExtraBodyValue(body, "image", images);
        }
        if (request.getN() != null) {
            body.put("n", request.getN());
        }
        if (request.getResponseFormat() != null && !request.getResponseFormat().isBlank()) {
            putExtraBodyValue(body, "response_format", request.getResponseFormat());
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private void putExtraBodyValue(Map<String, Object> body, String key, Object value) {
        Object exists = body.get("extra_body");
        Map<String, Object> extraBody;
        if (exists instanceof Map<?, ?> map) {
            extraBody = (Map<String, Object>) map;
        } else {
            extraBody = new LinkedHashMap<>();
            body.put("extra_body", extraBody);
        }
        extraBody.put(key, value);
    }

    private HttpRequest buildRequest(AiModelConfig cfg, Map<String, Object> body) {
        try {
            String json = HttpClientHolder.MAPPER.writeValueAsString(body);
            return HttpRequest.newBuilder()
                    .uri(URI.create(resolveUrl(cfg.getBaseUrl())))
                    .timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (Exception e) {
            throw new ServiceException("图片生成请求构建失败", e);
        }
    }

    /**
     * 兼容 base_url 写完整 /images/generations 或只写 /v1 的情况。
     */
    private String resolveUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ServiceException("图片模型 base_url 为空");
        }
        String trimmed = baseUrl.trim();
        if (trimmed.contains("/images/generations")) {
            return trimmed;
        }
        String stripped = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return stripped + "/images/generations";
    }

    private void applyModelParams(Map<String, Object> body, String modelParams) {
        if (modelParams == null || modelParams.isBlank()) return;
        try {
            JsonNode params = HttpClientHolder.MAPPER.readTree(modelParams);
            params.fields().forEachRemaining(entry ->
                    body.put(entry.getKey(), HttpClientHolder.MAPPER.convertValue(entry.getValue(), Object.class)));
        } catch (Exception e) {
            log.warn("图片模型参数解析失败，忽略参数：{}", e.getMessage());
        }
    }

    private List<String> parseImages(String body) {
        List<String> images = new ArrayList<>();
        try {
            JsonNode root = HttpClientHolder.MAPPER.readTree(body);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String url = item.path("url").asText("");
                    if (!url.isBlank()) {
                        images.add(url);
                        continue;
                    }
                    String b64 = item.path("b64_json").asText("");
                    if (!b64.isBlank()) {
                        images.add("data:image/png;base64," + b64);
                    }
                }
            }
            if (images.isEmpty()) {
                String url = root.path("url").asText("");
                if (!url.isBlank()) images.add(url);
            }
            if (images.isEmpty()) {
                String imageUrl = root.path("image_url").asText("");
                if (!imageUrl.isBlank()) images.add(imageUrl);
            }
        } catch (Exception e) {
            log.warn("图片生成响应解析失败：{}", e.getMessage());
        }
        return images;
    }

    private String summarizeError(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode node = HttpClientHolder.MAPPER.readTree(body);
            JsonNode error = node.path("error");
            if (!error.isMissingNode()) {
                String msg = error.path("message").asText("");
                if (!msg.isEmpty()) return truncate(msg);
                String code = error.path("code").asText("");
                if (!code.isEmpty()) return truncate(code);
            }
            String message = node.path("message").asText("");
            if (!message.isEmpty()) return truncate(message);
        } catch (Exception ignore) {
            // 非 JSON 直接降级
        }
        return truncate(body);
    }

    private String truncate(String text) {
        String clean = text.trim().replaceAll("\\s+", " ");
        return clean.length() > 200 ? clean.substring(0, 200) + "..." : clean;
    }
}
