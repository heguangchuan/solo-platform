package com.solo.openapi.web;

import tools.jackson.databind.ObjectMapper;
import com.solo.openapi.constant.OpenApiErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 开放 API 鉴权失败时直接在过滤器里写回 JSON（鉴权失败走不到 ControllerAdvice）。
 */
@Component
@RequiredArgsConstructor
public class OpenApiResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, OpenApiErrorCode errorCode,
                      String message, String requestId) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        OpenApiResult<Void> body = OpenApiResult.error(errorCode.getCode(),
                message != null ? message : errorCode.getMessage(), requestId);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
