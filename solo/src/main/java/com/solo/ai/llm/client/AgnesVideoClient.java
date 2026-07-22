package com.solo.ai.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.solo.common.ServiceException;
import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.llm.HttpClientHolder;
import com.solo.ai.llm.VideoGenerationClient;
import com.solo.ai.llm.VideoGenerationQueryResult;
import com.solo.ai.llm.VideoGenerationSubmitResult;
import com.solo.ai.request.VideoGenerationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agnes 视频生成客户端。
 */
@Component
@Slf4j
public class AgnesVideoClient implements VideoGenerationClient {

    @Override
    public VideoGenerationSubmitResult submit(AiModelConfig cfg, VideoGenerationRequest request) {
        Map<String, Object> body = buildBody(cfg, request);
        HttpRequest httpRequest = buildPostRequest(cfg, body);
        try {
            HttpResponse<String> response = HttpClientHolder.HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String summary = summarizeError(response.body());
                log.warn("视频任务创建失败：status={}, body={}", response.statusCode(), response.body());
                throw new ServiceException("视频任务创建失败：HTTP " + response.statusCode()
                        + (summary.isEmpty() ? "" : " - " + summary));
            }
            JsonNode root = HttpClientHolder.MAPPER.readTree(response.body());
            String taskId = text(root, "task_id", text(root, "id", null));
            String videoId = text(root, "video_id", null);
            if (taskId == null && videoId == null) {
                throw new ServiceException("视频任务响应格式异常");
            }
            return new VideoGenerationSubmitResult(taskId, videoId, text(root, "status", "queued"), text(root, "seconds", null), text(root, "size", null));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("视频任务创建异常：{}", e.getMessage(), e);
            throw new ServiceException("视频任务创建异常", e);
        }
    }

    @Override
    public VideoGenerationQueryResult query(AiModelConfig cfg, String taskId, String videoId) {
        HttpRequest request = buildGetRequest(cfg, taskId, videoId);
        try {
            HttpResponse<String> response = HttpClientHolder.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String summary = summarizeError(response.body());
                log.warn("视频任务查询失败：status={}, body={}", response.statusCode(), response.body());
                throw new ServiceException("视频任务查询失败：HTTP " + response.statusCode()
                        + (summary.isEmpty() ? "" : " - " + summary));
            }
            JsonNode root = HttpClientHolder.MAPPER.readTree(response.body());
            String error = null;
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                error = errorNode.isTextual() ? errorNode.asText() : errorNode.toString();
            }
            return new VideoGenerationQueryResult(
                    text(root, "task_id", text(root, "id", taskId)),
                    text(root, "video_id", videoId),
                    text(root, "status", ""),
                    root.path("progress").isNumber() ? root.path("progress").asInt() : null,
                    text(root, "remixed_from_video_id", text(root, "video_url", null)),
                    error,
                    text(root, "seconds", null),
                    text(root, "size", null)
            );
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("视频任务查询异常：{}", e.getMessage(), e);
            throw new ServiceException("视频任务查询异常", e);
        }
    }

    private Map<String, Object> buildBody(AiModelConfig cfg, VideoGenerationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModelName());
        body.put("prompt", request.getPrompt());
        applyModelParams(body, cfg.getModelParams());
        List<String> images = request.getImage() == null ? List.of() : request.getImage().stream()
                .filter(item -> item != null && !item.isBlank())
                .toList();
        if (images.isEmpty()) {
            if (request.getHeight() != null) body.put("height", request.getHeight());
            if (request.getWidth() != null) body.put("width", request.getWidth());
        }
        if (request.getNumFrames() != null) body.put("num_frames", request.getNumFrames());
        if (request.getFrameRate() != null) body.put("frame_rate", request.getFrameRate());
        if (request.getNumInferenceSteps() != null) body.put("num_inference_steps", request.getNumInferenceSteps());
        if (request.getSeed() != null) body.put("seed", request.getSeed());
        if (request.getNegativePrompt() != null && !request.getNegativePrompt().isBlank()) body.put("negative_prompt", request.getNegativePrompt());
        if (images.size() == 1 && (request.getMode() == null || request.getMode().isBlank() || "ti2vid".equals(request.getMode()))) {
            body.put("image", images.get(0));
            if (request.getMode() != null && !request.getMode().isBlank()) body.put("mode", request.getMode());
        } else if (!images.isEmpty()) {
            putExtraBodyValue(body, "image", images);
            if (request.getMode() != null && !request.getMode().isBlank()) putExtraBodyValue(body, "mode", request.getMode());
        } else if (request.getMode() != null && !request.getMode().isBlank()) {
            body.put("mode", request.getMode());
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

    private HttpRequest buildPostRequest(AiModelConfig cfg, Map<String, Object> body) {
        try {
            String json = HttpClientHolder.MAPPER.writeValueAsString(body);
            return HttpRequest.newBuilder()
                    .uri(URI.create(resolveCreateUrl(cfg.getBaseUrl())))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (Exception e) {
            throw new ServiceException("视频任务请求构建失败", e);
        }
    }

    private HttpRequest buildGetRequest(AiModelConfig cfg, String taskId, String videoId) {
        String url = videoId != null && !videoId.isBlank()
                ? resolveAgnesApiUrl(cfg.getBaseUrl(), videoId, cfg.getModelName())
                : resolveTaskUrl(cfg.getBaseUrl(), taskId);
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .GET()
                .build();
    }

    private String resolveCreateUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return "https://apihub.agnes-ai.com/v1/videos";
        String trimmed = baseUrl.trim();
        if (trimmed.contains("/v1/videos")) return trimmed;
        String stripped = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return stripped + "/v1/videos";
    }

    private String resolveTaskUrl(String baseUrl, String taskId) {
        String createUrl = resolveCreateUrl(baseUrl);
        String stripped = createUrl.endsWith("/") ? createUrl.substring(0, createUrl.length() - 1) : createUrl;
        return stripped + "/" + taskId;
    }

    private String resolveAgnesApiUrl(String baseUrl, String videoId, String modelName) {
        String host = "https://apihub.agnes-ai.com";
        if (baseUrl != null && !baseUrl.isBlank()) {
            URI uri = URI.create(resolveCreateUrl(baseUrl));
            host = uri.getScheme() + "://" + uri.getAuthority();
        }
        return host + "/agnesapi?video_id=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8)
                + "&model_name=" + URLEncoder.encode(modelName, StandardCharsets.UTF_8);
    }

    private void applyModelParams(Map<String, Object> body, String modelParams) {
        if (modelParams == null || modelParams.isBlank()) return;
        try {
            JsonNode params = HttpClientHolder.MAPPER.readTree(modelParams);
            params.fields().forEachRemaining(entry ->
                    body.put(entry.getKey(), HttpClientHolder.MAPPER.convertValue(entry.getValue(), Object.class)));
        } catch (Exception e) {
            log.warn("视频模型参数解析失败，忽略参数：{}", e.getMessage());
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
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
            String detail = node.path("detail").asText("");
            if (!detail.isEmpty()) return truncate(detail);
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
