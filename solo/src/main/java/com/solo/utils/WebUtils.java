package com.solo.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 请求相关工具：客户端 IP、来源、UA 提取。
 * 与 {@link com.solo.aop.OperationLogAspect} 中的实现保持一致，便于在非切面场景复用。
 */
public final class WebUtils {

    private WebUtils() {
    }

    /** 获取当前线程绑定的 HttpServletRequest，无则返回 null */
    public static HttpServletRequest currentRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 提取客户端真实 IP（兼容反代头） */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return truncate(comma > 0 ? ip.substring(0, comma).trim() : ip.trim(), 64);
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return truncate(ip, 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    /** 提取自定义客户端来源标识（X-Client-Source 头），缺省 null */
    public static String getClientSource(HttpServletRequest request) {
        if (request == null) return null;
        String source = request.getHeader("X-Client-Source");
        if (source == null || source.isBlank()) return null;
        return truncate(source.trim().toLowerCase(), 16);
    }

    /** 提取 User-Agent，最长 500 字符 */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        return truncate(request.getHeader("User-Agent"), 500);
    }

    public static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
