package com.solo.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.solo.common.ServiceException;
import com.solo.entity.AiModelConfig;
import com.solo.llm.HttpClientHolder;
import com.solo.llm.LlmClient;
import com.solo.service.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI Chat Completions 兼容协议客户端。
 * 适用于 OpenAI、Agnes、DeepSeek、通义、Kimi 等 "openai 兼容" 模式的供应商。
 */
@Component
@Slf4j
public class OpenAiCompatClient implements LlmClient {

    @Override
    public String chat(AiModelConfig cfg, List<AiChatService.ChatMessage> messages) {
        Map<String, Object> body = buildBody(cfg, messages, false);
        HttpRequest request = buildRequest(cfg, body, false);
        try {
            HttpResponse<String> response = HttpClientHolder.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAI兼容模型调用失败：status={}, body={}", response.statusCode(), response.body());
                throw new ServiceException("AI模型调用失败");
            }
            JsonNode root = HttpClientHolder.MAPPER.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new ServiceException("AI模型响应格式异常");
            }
            return contentNode.asText();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenAI兼容模型调用异常：{}", e.getMessage(), e);
            throw new ServiceException("AI模型调用异常", e);
        }
    }

    @Override
    public String streamChat(AiModelConfig cfg, List<AiChatService.ChatMessage> messages, Consumer<String> onChunk) {
        Map<String, Object> body = buildBody(cfg, messages, true);
        HttpRequest request = buildRequest(cfg, body, true);

        StringBuilder full = new StringBuilder();
        try {
            HttpResponse<java.io.InputStream> response = HttpClientHolder.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errBody;
                try (var es = response.body()) {
                    errBody = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                }
                log.warn("OpenAI兼容模型流式调用失败：status={}, body={}", response.statusCode(), errBody);
                throw new ServiceException("AI模型流式调用失败");
            }

            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode chunk = HttpClientHolder.MAPPER.readTree(data);
                        JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
                        if (!delta.isMissingNode() && !delta.isNull()) {
                            String piece = delta.asText();
                            if (!piece.isEmpty()) {
                                full.append(piece);
                                onChunk.accept(piece);
                            }
                        }
                    } catch (Exception ex) {
                        log.debug("OpenAI兼容流式分片解析失败: {}", ex.getMessage());
                    }
                }
            }
            return full.toString();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenAI兼容模型流式调用异常：{}", e.getMessage(), e);
            throw new ServiceException("AI模型流式调用异常", e);
        }
    }

    private Map<String, Object> buildBody(AiModelConfig cfg, List<AiChatService.ChatMessage> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModelName());
        body.put("messages", messages.stream()
                .map(this::toMessageJson)
                .toList());
        applyDefaultParams(body, cfg.getModelParams());
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    /**
     * 用户消息含图片时，content 改为多模态数组：[{type:text}, {type:image_url}...]
     * 其他情况保持字符串，最大兼容性。
     */
    private Map<String, Object> toMessageJson(AiChatService.ChatMessage msg) {
        if (!"user".equals(msg.role()) || !msg.hasAttachments()) {
            return Map.of("role", msg.role(), "content", msg.content() == null ? "" : msg.content());
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        String text = msg.content() == null ? "" : msg.content();
        if (!text.isEmpty()) {
            parts.add(Map.of("type", "text", "text", text));
        }
        for (AiChatService.Attachment att : msg.attachments()) {
            if (att.url() == null || att.url().isBlank()) continue;
            if (!"IMAGE".equalsIgnoreCase(att.type())) continue;
            Map<String, Object> imageUrl = new LinkedHashMap<>();
            imageUrl.put("url", att.url());
            parts.add(Map.of("type", "image_url", "image_url", imageUrl));
        }
        return Map.of("role", msg.role(), "content", parts);
    }

    private HttpRequest buildRequest(AiModelConfig cfg, Map<String, Object> body, boolean stream) {
        try {
            String json = HttpClientHolder.MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(resolveUrl(cfg.getBaseUrl())))
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (stream) {
                builder.header("Accept", "text/event-stream");
            }
            return builder.build();
        } catch (Exception e) {
            throw new ServiceException("AI模型请求构建失败", e);
        }
    }

    /**
     * 兼容历史配置：base_url 既可能写到完整 chat/completions 路径，也可能只是 /v1。
     */
    private String resolveUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ServiceException("AI模型 base_url 为空");
        }
        String trimmed = baseUrl.trim();
        if (trimmed.contains("/chat/completions")) {
            return trimmed;
        }
        String stripped = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return stripped + "/chat/completions";
    }

    private void applyDefaultParams(Map<String, Object> body, String modelParams) {
        if (modelParams == null || modelParams.isBlank()) {
            body.put("temperature", 0.5);
            body.put("max_tokens", 1024);
            return;
        }
        try {
            JsonNode params = HttpClientHolder.MAPPER.readTree(modelParams);
            params.fields().forEachRemaining(entry ->
                    body.put(entry.getKey(), HttpClientHolder.MAPPER.convertValue(entry.getValue(), Object.class)));
        } catch (Exception e) {
            log.warn("模型参数解析失败，使用默认参数：{}", e.getMessage());
            body.put("temperature", 0.5);
            body.put("max_tokens", 1024);
        }
    }
}
