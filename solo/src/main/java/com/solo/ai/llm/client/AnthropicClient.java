package com.solo.ai.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.solo.common.ServiceException;
import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.llm.AttachmentFetcher;
import com.solo.ai.llm.HttpClientHolder;
import com.solo.ai.llm.LlmClient;
import com.solo.ai.service.AiChatService;
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
 * Anthropic Messages API 客户端。
 * 文档：POST https://api.anthropic.com/v1/messages
 */
@Component
@Slf4j
public class AnthropicClient implements LlmClient {

    private static final String DEFAULT_VERSION = "2023-06-01";

    @Override
    public String chat(AiModelConfig cfg, List<AiChatService.ChatMessage> messages) {
        Map<String, Object> body = buildBody(cfg, messages, false);
        HttpRequest request = buildRequest(cfg, body, false);
        try {
            HttpResponse<String> response = HttpClientHolder.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Anthropic 调用失败：status={}, body={}", response.statusCode(), response.body());
                throw new ServiceException("AI模型调用失败：HTTP " + response.statusCode());
            }
            JsonNode root = HttpClientHolder.MAPPER.readTree(response.body());
            JsonNode contentArr = root.path("content");
            StringBuilder sb = new StringBuilder();
            if (contentArr.isArray()) {
                for (JsonNode part : contentArr) {
                    if ("text".equals(part.path("type").asText())) {
                        sb.append(part.path("text").asText());
                    }
                }
            }
            if (sb.length() == 0) {
                throw new ServiceException("AI模型响应格式异常");
            }
            return sb.toString();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Anthropic 调用异常：{}", e.getMessage(), e);
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
                log.warn("Anthropic 流式调用失败：status={}, body={}", response.statusCode(), errBody);
                throw new ServiceException("AI模型流式调用失败：HTTP " + response.statusCode());
            }

            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                String currentEvent = "";
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        currentEvent = "";
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                        continue;
                    }
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;
                    if (!"content_block_delta".equals(currentEvent)) continue;
                    try {
                        JsonNode chunk = HttpClientHolder.MAPPER.readTree(data);
                        JsonNode delta = chunk.path("delta");
                        if ("text_delta".equals(delta.path("type").asText())) {
                            String piece = delta.path("text").asText("");
                            if (!piece.isEmpty()) {
                                full.append(piece);
                                onChunk.accept(piece);
                            }
                        }
                    } catch (Exception ex) {
                        log.debug("Anthropic 流式分片解析失败: {}", ex.getMessage());
                    }
                }
            }
            return full.toString();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Anthropic 流式调用异常：{}", e.getMessage(), e);
            throw new ServiceException("AI模型流式调用异常", e);
        }
    }

    private Map<String, Object> buildBody(AiModelConfig cfg, List<AiChatService.ChatMessage> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModelName());

        // Anthropic 要求 system 单独字段，messages 中只允许 user/assistant
        StringBuilder systemPrompt = new StringBuilder();
        List<Map<String, Object>> chatMessages = new ArrayList<>();
        for (AiChatService.ChatMessage msg : messages) {
            if ("system".equals(msg.role())) {
                if (systemPrompt.length() > 0) systemPrompt.append("\n\n");
                systemPrompt.append(msg.content());
            } else {
                chatMessages.add(toAnthropicMessage(msg));
            }
        }
        if (systemPrompt.length() > 0) {
            body.put("system", systemPrompt.toString());
        }
        body.put("messages", chatMessages);
        applyParams(body, cfg.getModelParams());
        // Anthropic 强制需要 max_tokens
        body.putIfAbsent("max_tokens", 1024);
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    /**
     * Anthropic 多模态：content 是 [{type:text}, {type:image, source:{type:base64, media_type, data}}] 数组。
     * 用 base64 内联是为了让模型服务端不需要回头访问我们的图片 URL（私有部署时常常不可达）。
     */
    private Map<String, Object> toAnthropicMessage(AiChatService.ChatMessage msg) {
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
            String dataUri = AttachmentFetcher.toDataUri(att.url(), att.mimeType());
            if (dataUri == null) continue;
            int comma = dataUri.indexOf(',');
            if (comma < 0) continue;
            String header = dataUri.substring(5, dataUri.indexOf(';')); // skip "data:" 取 mime
            String base64 = dataUri.substring(comma + 1);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "base64");
            source.put("media_type", header);
            source.put("data", base64);
            parts.add(Map.of("type", "image", "source", source));
        }
        return Map.of("role", msg.role(), "content", parts);
    }

    private HttpRequest buildRequest(AiModelConfig cfg, Map<String, Object> body, boolean stream) {
        try {
            String json = HttpClientHolder.MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(resolveUrl(cfg.getBaseUrl())))
                    .header("x-api-key", cfg.getApiKey())
                    .header("anthropic-version", DEFAULT_VERSION)
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

    private String resolveUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ServiceException("AI模型 base_url 为空");
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/messages")) {
            return trimmed;
        }
        String stripped = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return stripped + "/messages";
    }

    private void applyParams(Map<String, Object> body, String modelParams) {
        if (modelParams == null || modelParams.isBlank()) return;
        try {
            JsonNode params = HttpClientHolder.MAPPER.readTree(modelParams);
            params.fields().forEachRemaining(entry ->
                    body.put(entry.getKey(), HttpClientHolder.MAPPER.convertValue(entry.getValue(), Object.class)));
        } catch (Exception e) {
            log.warn("Anthropic 模型参数解析失败：{}", e.getMessage());
        }
    }
}
