package com.solo.openapi.web;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.solo.common.Result;
import com.solo.openapi.support.OpenApiContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 把开放 Controller 的返回值统一包成 {@link OpenApiResult}（含 requestId）。
 * Controller 直接返回业务对象即可。
 */
@RestControllerAdvice(basePackages = "com.solo.openapi.controller")
@RequiredArgsConstructor
public class OpenApiResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        String rid = OpenApiContextHolder.requestId(servletRequest);

        if (body instanceof OpenApiResult<?>) {
            return body;
        }
        if (body instanceof Result<?>) {
            return body;
        }
        if (body instanceof String) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return objectMapper.writeValueAsString(OpenApiResult.success(body, rid));
            } catch (JacksonException e) {
                throw new IllegalStateException(e);
            }
        }
        return OpenApiResult.success(body, rid);
    }
}
