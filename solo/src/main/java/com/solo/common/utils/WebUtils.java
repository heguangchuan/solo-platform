package com.solo.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

        if (!isValidIp(ip)) {
            ip = request.getRemoteAddr();
        }

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

        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return truncate(ip != null ? ip : UNKNOWN, 64);
    }

    public static String getClientIp() {
        return getClientIp(currentRequest());
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }
}
