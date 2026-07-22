package com.solo.common.utils;

import com.solo.system.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前用户上下文工具类
 * 通过 RequestContextHolder 获取当前请求中的用户信息，无需手动传参
 */
@Component
public class UserHolderUtils {

    private static final String CURRENT_USER_ID = "currentUserId";
    private static final String CURRENT_USER_NAME = "currentUsername";
    private static final String CURRENT_USER = "currentUser";

    /**
     * 获取当前请求的 HttpServletRequest
     */
    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new IllegalStateException("当前没有 HTTP 请求上下文");
        return attrs.getRequest();
    }

    /**
     * 获取当前登录用户ID
     */
    public static Long getCurrentUserId() {
        Object id = getRequest().getAttribute(CURRENT_USER_ID);
        return id != null ? ((Number) id).longValue() : null;
    }

    /**
     * 获取当前登录用户名
     */
    public static String getCurrentUsername() {
        Object name = getRequest().getAttribute(CURRENT_USER_NAME);
        return name != null ? name.toString() : null;
    }

    /**
     * 获取当前登录用户完整对象
     */
    @SuppressWarnings("unchecked")
    public static SysUser getCurrentUser() {
        return (SysUser) getRequest().getAttribute(CURRENT_USER);
    }
}
