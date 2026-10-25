package com.solo.openapi.web;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.solo.openapi.constant.OpenApiConstants;
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
        Object requestId = servletRequest.getAttribute(OpenApiConstants.HEADER_REQUEST_ID);
        String rid = requestId != null ? requestId.toString() : null;

        if (body instanceof OpenApiResult<?> openApiResult) {
            return openApiResult;
        }
        // String 返回值走 StringHttpMessageConverter，不能直接塞对象，手动序列化
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
