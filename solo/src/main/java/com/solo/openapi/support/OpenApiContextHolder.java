package com.solo.openapi.support;

import com.solo.openapi.auth.OpenAppContext;
import com.solo.openapi.constant.OpenApiConstants;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从当前请求中取开放 API 应用身份。
 */
public final class OpenApiContextHolder {

    private OpenApiContextHolder() {
    }

    public static OpenAppContext get(HttpServletRequest request) {
        Object ctx = request.getAttribute(OpenApiConstants.ATTR_APP_CONTEXT);
        return ctx instanceof OpenAppContext c ? c : null;
    }

    public static OpenAppContext current() {
        HttpServletRequest request = com.solo.common.utils.WebUtils.currentRequest();
        return request == null ? null : get(request);
    }
}
