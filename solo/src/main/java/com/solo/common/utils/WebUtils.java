package com.solo.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 请求相关工具：客户端 IP、来源、UA 提取。
 * <p>IP 获取兼容主流反向代理 / CDN / WAF 场景。
 */
public final class WebUtils {

    private static final String UNKNOWN = "unknown";
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "X-Cluster-Client-IP"
    };

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

    /**
     * 提取客户端真实 IP（兼容反代 / CDN / WAF 场景）
     * <p>按优先级依次尝试 11 种常见代理头，最终 fallback 到 getRemoteAddr()
     * <p>多级代理时取第一个有效 IP（最左侧）
     *
     * @param request HTTP 请求
     * @return IP 地址（自动截断至 64 字符），获取失败返回 "unknown"
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String ip = null;
        for (String header : IP_HEADERS) {
            ip = request.getHeader(header);
            if (isValidIp(ip)) {
                break;
            }
        }

        // 都没拿到，fallback 到 remoteAddr
        if (!isValidIp(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多级代理时 X-Forwarded-For 格式为：clientIp, proxy1, proxy2...
        // 遍历找到第一个有效 IP
        if (ip != null && ip.contains(",")) {
            String[] ips = ip.split(",");
            for (String s : ips) {
                String trimmed = s.trim();
                if (isValidIp(trimmed)) {
                    ip = trimmed;
                    break;
                }
            }
        }

        // IPv6 本地回环转成 IPv4 格式
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return truncate(ip != null ? ip : UNKNOWN, 64);
    }

    public static String getClientIp() {
        return getClientIp(currentRequest());
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

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }
}
