package com.solo.openapi.auth;

import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.exception.OpenApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * Scope 校验：鉴权过滤器通过后，按 @RequireScope 检查应用授权范围。
 */
@Component
public class OpenApiScopeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireScope annotation = handlerMethod.getMethodAnnotation(RequireScope.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireScope.class);
        }
        if (annotation == null) {
            return true;
        }

        Object ctxAttr = request.getAttribute(OpenApiConstants.ATTR_APP_CONTEXT);
        if (!(ctxAttr instanceof OpenAppContext context)) {
            // 理论上过滤器已兜底，防御性处理
            throw new OpenApiException(OpenApiErrorCode.MISSING_CREDENTIAL);
        }

        boolean allowed = Arrays.stream(annotation.value()).anyMatch(context::hasScope);
        if (!allowed) {
            throw new OpenApiException(OpenApiErrorCode.SCOPE_DENIED);
        }
        return true;
    }
}
