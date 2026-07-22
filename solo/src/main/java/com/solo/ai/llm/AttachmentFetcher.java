package com.solo.ai.llm;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * 多模态附件辅助：把图片 URL 拉取下来并转成 data URI（base64 内联）。
 *
 * <p>背景：模型供应商（Agnes / OpenAI / Anthropic / Gemini 等）的服务器在拼装请求时会回头访问
 * 我们传过去的 image_url，但本地开发或私有部署的 MinIO/本地存储常常只能在内网访问，模型
 * 服务器自然连不上。最稳妥的做法是后端先把图片下载下来，转成
 * {@code data:<mime>;base64,...} 形式发给模型，模型不再回头拉文件。</p>
 */
@Slf4j
public final class AttachmentFetcher {

    /** 单张图片下载超时 */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    /** 单张图片大小上限（5MB），超过会截断为空字符串导致跳过 */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private AttachmentFetcher() {
    }

    /**
     * 把 URL（可能是 http/https）拉取后转换成 data URI；本身已经是 data URI 时原样返回。
     *
     * @param url      原始访问地址
     * @param mimeHint 数据库里记录的 mime（可选，作为兜底）
     * @return data URI；任何失败返回 null，调用方应跳过该附件
     */
    public static String toDataUri(String url, String mimeHint) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("data:")) return url;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HttpClientHolder.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("拉取附件失败：status={}, url={}", resp.statusCode(), url);
                return null;
            }
            byte[] bytes = resp.body();
            if (bytes == null || bytes.length == 0) {
                log.warn("拉取附件失败：响应为空, url={}", url);
                return null;
            }
            if (bytes.length > MAX_BYTES) {
                log.warn("附件过大被跳过：size={}, url={}", bytes.length, url);
                return null;
            }
            String mime = pickMime(resp.headers().firstValue("content-type").orElse(null), mimeHint, url);
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("拉取附件异常：url={}, msg={}", url, e.getMessage());
            return null;
        }
    }

    private static String pickMime(String responseHeader, String hint, String url) {
        if (responseHeader != null && !responseHeader.isBlank()) {
            int semi = responseHeader.indexOf(';');
            return semi > 0 ? responseHeader.substring(0, semi).trim() : responseHeader.trim();
        }
        if (hint != null && !hint.isBlank()) return hint;
        String lower = url.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/png";
    }
}
