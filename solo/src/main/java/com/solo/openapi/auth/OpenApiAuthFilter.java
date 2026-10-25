package com.solo.openapi.auth;

import com.solo.common.utils.WebUtils;
import com.solo.openapi.config.OpenApiProperties;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.constant.OpenApiRedisKeys;
import com.solo.openapi.entity.OpenApiLog;
import com.solo.openapi.entity.OpenApp;
import com.solo.openapi.exception.OpenApiException;
import com.solo.openapi.service.OpenApiAppService;
import com.solo.openapi.service.OpenApiAuditService;
import com.solo.openapi.support.OpenApiSigner;
import com.solo.openapi.web.CachedBodyHttpServletRequest;
import com.solo.openapi.web.OpenApiResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 开放 API 鉴权过滤器（仅拦 /open-api/**）：
 * 应用状态 → 时间戳 → 防重放 → HMAC 签名 → IP 白名单 → 限流。
 * 通过后把 {@link OpenAppContext} 写入请求属性，Scope 校验交给拦截器。
 */
@Component
public class OpenApiAuthFilter extends OncePerRequestFilter {

    private final OpenApiAppService appService;
    private final OpenApiRateLimiter rateLimiter;
    private final OpenApiIpChecker ipChecker;
    private final OpenApiResponseWriter responseWriter;
    private final OpenApiAuditService auditService;
    private final OpenApiProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;

    public OpenApiAuthFilter(OpenApiAppService appService,
                             OpenApiRateLimiter rateLimiter,
                             OpenApiIpChecker ipChecker,
                             OpenApiResponseWriter responseWriter,
                             OpenApiAuditService auditService,
                             OpenApiProperties properties,
                             RedisTemplate<String, Object> redisTemplate) {
        this.appService = appService;
        this.rateLimiter = rateLimiter;
        this.ipChecker = ipChecker;
        this.responseWriter = responseWriter;
        this.auditService = auditService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(OpenApiConstants.PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String requestId = resolveRequestId(request);
        MDC.put("requestId", requestId);
        response.setHeader(OpenApiConstants.HEADER_REQUEST_ID, requestId);

        String appKey = request.getHeader(OpenApiConstants.HEADER_APP_KEY);
        OpenApiErrorCode rejectCode = null;
        String rejectMessage = null;
        CachedBodyHttpServletRequest cachedRequest = null;

        try {
            if (!properties.isEnabled()) {
                throw new OpenApiException(OpenApiErrorCode.APP_DISABLED, "开放API未启用");
            }

            String timestamp = request.getHeader(OpenApiConstants.HEADER_TIMESTAMP);
            String nonce = request.getHeader(OpenApiConstants.HEADER_NONCE);
            String signature = request.getHeader(OpenApiConstants.HEADER_SIGNATURE);
            if (!StringUtils.hasText(appKey) || !StringUtils.hasText(timestamp)
                    || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
                throw new OpenApiException(OpenApiErrorCode.MISSING_CREDENTIAL);
            }

            OpenApiAppService.AppSecret appSecret = appService.loadByAppKey(appKey);
            if (appSecret == null) {
                throw new OpenApiException(OpenApiErrorCode.APP_NOT_FOUND);
            }
            OpenApp app = appSecret.app();

            if (app.getStatus() == null || app.getStatus() != 1) {
                throw new OpenApiException(OpenApiErrorCode.APP_DISABLED);
            }
            if (app.getExpireTime() != null && app.getExpireTime().isBefore(LocalDateTime.now())) {
                throw new OpenApiException(OpenApiErrorCode.APP_EXPIRED);
            }

            long ts = parseTimestamp(timestamp);
            if (Math.abs(System.currentTimeMillis() / 1000 - ts) > OpenApiConstants.TIMESTAMP_TOLERANCE_SECONDS) {
                throw new OpenApiException(OpenApiErrorCode.TIMESTAMP_EXPIRED);
            }

            // nonce 去重：setIfAbsent 原子占位，窗口期内同一 nonce 直接拒
            String nonceKey = OpenApiRedisKeys.NONCE_PREFIX + appKey + ":" + nonce;
            Boolean firstTime = redisTemplate.opsForValue()
                    .setIfAbsent(nonceKey, "1", Duration.ofSeconds(OpenApiConstants.NONCE_TTL_SECONDS));
            if (!Boolean.TRUE.equals(firstTime)) {
                throw new OpenApiException(OpenApiErrorCode.NONCE_DUPLICATED);
            }

            // 签名：body 需要读全量字节，用缓存包装让 Controller 还能再读一遍
            cachedRequest = new CachedBodyHttpServletRequest(request);
            String bodyHash = OpenApiSigner.sha256Hex(cachedRequest.getBodyString());
            String canonicalQuery = OpenApiSigner.canonicalQuery(request.getParameterMap());
            String signString = OpenApiSigner.buildSignString(request.getMethod(), request.getRequestURI(),
                    canonicalQuery, bodyHash, timestamp, nonce);

            String expected = OpenApiSigner.hmacSha256Hex(signString, appSecret.secret());
            boolean matched = OpenApiSigner.matches(expected, signature);
            // 当前密钥不匹配时，再试过渡期内的旧密钥
            if (!matched && allowOldSecret(app) && StringUtils.hasText(appSecret.oldSecret())) {
                String oldExpected = OpenApiSigner.hmacSha256Hex(signString, appSecret.oldSecret());
                matched = OpenApiSigner.matches(oldExpected, signature);
            }
            if (!matched) {
                throw new OpenApiException(OpenApiErrorCode.SIGNATURE_INVALID);
            }

            String clientIp = WebUtils.getClientIp(request);
            if (!ipChecker.allowed(app.getIpWhitelist(), clientIp)) {
                throw new OpenApiException(OpenApiErrorCode.IP_DENIED);
            }

            if (!rateLimiter.tryAcquireQps(app)) {
                throw new OpenApiException(OpenApiErrorCode.RATE_LIMITED);
            }
            if (!rateLimiter.tryAcquireDaily(app)) {
                throw new OpenApiException(OpenApiErrorCode.DAILY_QUOTA_EXCEEDED);
            }

            OpenAppContext context = new OpenAppContext(app.getId(), app.getAppKey(), app.getAppName(),
                    appService.parseScopes(app.getScopes()), app);
            cachedRequest.setAttribute(OpenApiConstants.ATTR_APP_CONTEXT, context);
            cachedRequest.setAttribute(OpenApiConstants.HEADER_REQUEST_ID, requestId);

            chain.doFilter(cachedRequest, response);
        } catch (OpenApiException e) {
            rejectCode = e.getErrorCode();
            rejectMessage = e.getMessage();
            responseWriter.write(response, e.getErrorCode(), e.getMessage(), requestId);
        } catch (Exception e) {
            rejectCode = OpenApiErrorCode.INTERNAL_ERROR;
            rejectMessage = "平台内部错误";
            responseWriter.write(response, OpenApiErrorCode.INTERNAL_ERROR, null, requestId);
        } finally {
            audit(request, response, appKey, requestId, start, rejectCode, rejectMessage);
            MDC.remove("requestId");
        }
    }

    private boolean allowOldSecret(OpenApp app) {
        return app.getOldSecretExpireTime() == null
                || app.getOldSecretExpireTime().isAfter(LocalDateTime.now());
    }

    private long parseTimestamp(String timestamp) {
        try {
            long ts = Long.parseLong(timestamp);
            // 兼容毫秒时间戳
            return ts > 9_999_999_999L ? ts / 1000 : ts;
        } catch (NumberFormatException e) {
            throw new OpenApiException(OpenApiErrorCode.TIMESTAMP_EXPIRED, "时间戳格式错误");
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader(OpenApiConstants.HEADER_REQUEST_ID);
        if (StringUtils.hasText(header)) {
            return header.substring(0, Math.min(header.length(), 36));
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void audit(HttpServletRequest request, HttpServletResponse response, String appKey,
                       String requestId, long start, OpenApiErrorCode rejectCode, String rejectMessage) {
        if (!properties.isAuditLogEnabled()) {
            return;
        }
        try {
            OpenApiLog entry = new OpenApiLog();
            entry.setAppKey(appKey);
            entry.setRequestId(requestId);
            entry.setMethod(request.getMethod());
            entry.setPath(request.getRequestURI());
            String qs = request.getQueryString();
            entry.setQueryString(qs != null && qs.length() > 1000 ? qs.substring(0, 1000) : qs);
            entry.setClientIp(WebUtils.getClientIp(request));
            entry.setHttpStatus(rejectCode != null ? rejectCode.getHttpStatus() : response.getStatus());
            entry.setBizCode(rejectCode != null ? rejectCode.getCode() : OpenApiErrorCode.SUCCESS.getCode());
            entry.setCostMs(System.currentTimeMillis() - start);
            entry.setErrorMsg(rejectMessage);
            auditService.recordAsync(entry);
        } catch (Exception ignored) {
            // 审计失败不影响主链路
        }
    }
}
