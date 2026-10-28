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

    /** 请求 ID：鉴权过滤器写入请求属性，未经过滤器时回退请求头 */
    public static String requestId(HttpServletRequest request) {
        Object rid = request.getAttribute(OpenApiConstants.HEADER_REQUEST_ID);
        return rid != null ? rid.toString() : request.getHeader(OpenApiConstants.HEADER_REQUEST_ID);
    }
}
