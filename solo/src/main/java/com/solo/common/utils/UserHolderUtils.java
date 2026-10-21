package com.solo.common.utils;

import com.solo.system.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class UserHolderUtils {

    private static final String CURRENT_USER_ID = "currentUserId";
    private static final String CURRENT_USER_NAME = "currentUsername";
    private static final String CURRENT_USER = "currentUser";

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new IllegalStateException("当前没有 HTTP 请求上下文");
        return attrs.getRequest();
    }

    public static Long getCurrentUserId() {
        Object id = getRequest().getAttribute(CURRENT_USER_ID);
        return id != null ? ((Number) id).longValue() : null;
    }

    public static String getCurrentUsername() {
        Object name = getRequest().getAttribute(CURRENT_USER_NAME);
        return name != null ? name.toString() : null;
    }

    public static SysUser getCurrentUser() {
        return (SysUser) getRequest().getAttribute(CURRENT_USER);
    }
}
