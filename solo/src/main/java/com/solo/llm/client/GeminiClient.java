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
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Google Gemini (generativelanguage.googleapis.com) 客户端。
 * 文档：POST /v1beta/models/{model}:generateContent (非流式)
 *      POST /v1beta/models/{model}:streamGenerateContent?alt=sse (流式)
 */
@Component
@Slf4j
public class GeminiClient implements LlmClient {

    /**
     * 已知的 OpenAI 风格参数与 Gemini generationConfig 字段映射。
     */
    private static final Map<String, String> PARAM_MAPPING = Map.of(
            "temperature", "temperature",
            "top_p", "topP",
            "top_k", "topK",
            "max_tokens", "maxOutputTokens",
            "stop", "stopSequences"
    );

    private static final Set<String> KNOWN_GENERATION_KEYS = Set.of(
            "temperature", "topP", "topK", "maxOutputTokens", "stopSequences",
            "candidateCount", "responseMimeType", "responseSchema"
    );

    @Override
    public String chat(AiModelConfig cfg, List<AiChatService.ChatMessage> messages) {
        Map<String, Object> body = buildBody(cfg, messages);
        HttpRequest request = buildRequest(cfg, body, false);
        try {
            HttpResponse<String> response = HttpClientHolder.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Gemini 调用失败：status={}, body={}", response.statusCode(), response.body());
                throw new ServiceException("AI模型调用失败");
            }
            JsonNode root = HttpClientHolder.MAPPER.readTree(response.body());
            return extractText(root);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini 调用异常：{}", e.getMessage(), e);
            throw new ServiceException("AI模型调用异常", e);
        }
    }

    @Override
    public String streamChat(AiModelConfig cfg, List<AiChatService.ChatMessage> messages, Consumer<String> onChunk) {
        Map<String, Object> body = buildBody(cfg, messages);
        HttpRequest request = buildRequest(cfg, body, true);

        StringBuilder full = new StringBuilder();
        try {
            HttpResponse<java.io.InputStream> response = HttpClientHolder.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errBody;
                try (var es = response.body()) {
                    errBody = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                }
                log.warn("Gemini 流式调用失败：status={}, body={}", response.statusCode(), errBody);
                throw new ServiceException("AI模型流式调用失败");
            }

            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;
                    try {
                        JsonNode chunk = HttpClientHolder.MAPPER.readTree(data);
                        String piece = extractText(chunk);
                        if (!piece.isEmpty()) {
                            full.append(piece);
                            onChunk.accept(piece);
                        }
                    } catch (Exception ex) {
                        log.debug("Gemini 流式分片解析失败: {}", ex.getMessage());
                    }
                }
            }
            return full.toString();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini 流式调用异常：{}", e.getMessage(), e);
            throw new ServiceException("AI模型流式调用异常", e);
        }
    }

    private Map<String, Object> buildBody(AiModelConfig cfg, List<AiChatService.ChatMessage> messages) {
        Map<String, Object> body = new LinkedHashMap<>();

        // 抽出 system 角色
        StringBuilder systemPrompt = new StringBuilder();
        List<Map<String, Object>> contents = new ArrayList<>();
        for (AiChatService.ChatMessage msg : messages) {
            if ("system".equals(msg.role())) {
                if (systemPrompt.length() > 0) systemPrompt.append("\n\n");
                systemPrompt.append(msg.content());
            } else {
                String role = "assistant".equals(msg.role()) ? "model" : "user";
                contents.add(Map.of(
                        "role", role,
                        "parts", buildParts(msg)
                ));
            }
        }
        if (systemPrompt.length() > 0) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt.toString()))
            ));
        }
        body.put("contents", contents);

        Map<String, Object> generationConfig = buildGenerationConfig(cfg.getModelParams());
        if (!generationConfig.isEmpty()) {
            body.put("generationConfig", generationConfig);
        }
        return body;
    }

    /**
     * Gemini parts 数组：text + fileData(URI) 形式承载多模态。
     * 注意 Gemini 要求图片 URL 公开可达。
     */
    private List<Map<String, Object>> buildParts(AiChatService.ChatMessage msg) {
        List<Map<String, Object>> parts = new ArrayList<>();
        String text = msg.content() == null ? "" : msg.content();
        if (!text.isEmpty()) {
            parts.add(Map.of("text", text));
        }
        if (msg.hasAttachments()) {
            for (AiChatService.Attachment att : msg.attachments()) {
                if (att.url() == null || att.url().isBlank()) continue;
                if (!"IMAGE".equalsIgnoreCase(att.type())) continue;
                Map<String, Object> fileData = new LinkedHashMap<>();
                fileData.put("fileUri", att.url());
                if (att.mimeType() != null && !att.mimeType().isBlank()) {
                    fileData.put("mimeType", att.mimeType());
                } else {
                    fileData.put("mimeType", "image/png");
                }
                parts.add(Map.of("fileData", fileData));
            }
        }
        if (parts.isEmpty()) {
            parts.add(Map.of("text", ""));
        }
        return parts;
    }

    private Map<String, Object> buildGenerationConfig(String modelParams) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (modelParams == null || modelParams.isBlank()) {
            config.put("temperature", 0.5);
            config.put("maxOutputTokens", 1024);
            return config;
        }
        try {
            JsonNode params = HttpClientHolder.MAPPER.readTree(modelParams);
            params.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                Object value = HttpClientHolder.MAPPER.convertValue(entry.getValue(), Object.class);
                String mapped = PARAM_MAPPING.get(key);
                if (mapped != null) {
                    config.put(mapped, value);
                } else if (KNOWN_GENERATION_KEYS.contains(key)) {
                    config.put(key, value);
                }
            });
        } catch (Exception e) {
            log.warn("Gemini 模型参数解析失败：{}", e.getMessage());
        }
        return config;
    }

    private HttpRequest buildRequest(AiModelConfig cfg, Map<String, Object> body, boolean stream) {
        try {
            String json = HttpClientHolder.MAPPER.writeValueAsString(body);
            return HttpRequest.newBuilder()
                    .uri(URI.create(resolveUrl(cfg, stream)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (Exception e) {
            throw new ServiceException("AI模型请求构建失败", e);
        }
    }

    private String resolveUrl(AiModelConfig cfg, boolean stream) {
        String baseUrl = cfg.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ServiceException("AI模型 base_url 为空");
        }
        String trimmed = baseUrl.trim();
        String apiKey = URLEncoder.encode(cfg.getApiKey() == null ? "" : cfg.getApiKey(), StandardCharsets.UTF_8);

        // 如果用户已经把完整路径塞进 base_url（含 :generateContent 或 :streamGenerateContent），原样使用
        if (trimmed.contains(":generateContent") || trimmed.contains(":streamGenerateContent")) {
            return appendKey(trimmed, apiKey, stream);
        }

        String stripped = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        String method = stream ? ":streamGenerateContent" : ":generateContent";
        String url = stripped + "/models/" + cfg.getModelName() + method;
        return appendKey(url, apiKey, stream);
    }

    private String appendKey(String url, String apiKey, boolean stream) {
        StringBuilder sb = new StringBuilder(url);
        boolean hasQuery = url.contains("?");
        if (stream && !url.contains("alt=")) {
            sb.append(hasQuery ? "&" : "?").append("alt=sse");
            hasQuery = true;
        }
        if (!apiKey.isEmpty() && !url.contains("key=")) {
            sb.append(hasQuery ? "&" : "?").append("key=").append(apiKey);
        }
        return sb.toString();
    }

    private String extractText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode text = part.path("text");
            if (!text.isMissingNode() && !text.isNull()) {
                sb.append(text.asText());
            }
        }
        return sb.toString();
    }
}
