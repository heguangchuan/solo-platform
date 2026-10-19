package com.solo.aop;

import com.solo.common.enums.BusinessType;
import com.solo.system.entity.SysLog;
import com.solo.system.request.LoginRequest;
import com.solo.system.service.SysLogService;
import com.solo.common.utils.UserHolderUtils;
import com.solo.common.utils.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 操作日志切面
 * 拦截 @OperationLog 注解的方法，异步写入 sys_log
 * 主流程不阻塞，日志失败仅 warn 不抛出
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    private static final int PARAM_MAX_LEN = 4000;
    private static final int ERROR_MAX_LEN = 2000;

    private final SysLogService sysLogService;
    private final ThreadPoolTaskExecutor logExecutor;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(SysLogService sysLogService,
                              @Qualifier("logExecutor") ThreadPoolTaskExecutor logExecutor,
                              ObjectMapper objectMapper) {
        this.sysLogService = sysLogService;
        this.logExecutor = logExecutor;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Throwable error = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                long cost = System.currentTimeMillis() - startTime;
                SysLog sysLog = buildLog(joinPoint, operationLog, cost, error);
                logExecutor.execute(() -> {
                    try {
                        sysLogService.save(sysLog);
                    } catch (Exception e) {
                        log.warn("写入操作日志失败: {}", e.getMessage());
                    }
                });
            } catch (Exception e) {
                log.warn("构建操作日志失败: {}", e.getMessage());
            }
        }
    }

    private SysLog buildLog(ProceedingJoinPoint joinPoint, OperationLog ann, long cost, Throwable error) {
        SysLog sysLog = new SysLog();
        sysLog.setModule(ann.module());
        sysLog.setBusinessType(ann.type().name());
        sysLog.setCostMs(cost);
        sysLog.setResponseStatus(error == null ? 1 : 0);
        if (error != null) {
            sysLog.setErrorMsg(truncate(error.getMessage(), ERROR_MAX_LEN));
        }
        sysLog.setCreateTime(LocalDateTime.now());

        // 操作人
        try {
            sysLog.setOperatorId(UserHolderUtils.getCurrentUserId());
            sysLog.setOperatorName(UserHolderUtils.getCurrentUsername());
        } catch (Exception ignored) {
        }
        // 登录场景下当前用户尚未写入请求属性，从 LoginRequest 取用户名兜底
        if (sysLog.getOperatorName() == null && ann.type() == BusinessType.LOGIN) {
            for (Object arg : joinPoint.getArgs()) {
                if (arg instanceof LoginRequest login && login.getUsername() != null) {
                    sysLog.setOperatorName(truncate(login.getUsername(), 50));
                    break;
                }
            }
        }

        // 请求上下文
        HttpServletRequest request = currentRequest();
        if (request != null) {
            sysLog.setRequestMethod(request.getMethod());
            sysLog.setRequestUrl(truncate(request.getRequestURI(), 255));
            sysLog.setClientIp(WebUtils.getClientIp(request));
            sysLog.setUserAgent(WebUtils.getUserAgent(request));
            sysLog.setClientSource(resolveClientSource(request));
        }

        // 描述
        String description = ann.description();
        if (description == null || description.isEmpty()) {
            description = resolveMethodName(joinPoint);
        }
        sysLog.setDescription(truncate(description, 255));

        // 参数
        if (ann.recordParam()) {
            sysLog.setRequestParam(serializeArgs(joinPoint));
        }
        return sysLog;
    }

    private HttpServletRequest currentRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String resolveClientSource(HttpServletRequest request) {
        String source = request.getHeader("X-Client-Source");
        if (source == null || source.isBlank()) {
            return null;
        }
        return truncate(source.trim().toLowerCase(), 16);
    }

    private String resolveMethodName(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            return method.getDeclaringClass().getSimpleName() + "." + method.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeArgs(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> filtered = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null) {
                filtered.add(null);
            } else if (arg instanceof jakarta.servlet.ServletRequest
                    || arg instanceof jakarta.servlet.ServletResponse
                    || arg instanceof MultipartFile
                    || arg instanceof MultipartFile[]) {
                filtered.add("<binary>");
            } else {
                filtered.add(arg);
            }
        }
        try {
            String json = objectMapper.writeValueAsString(filtered.size() == 1 ? filtered.get(0) : filtered);
            return truncate(json, PARAM_MAX_LEN);
        } catch (Exception e) {
            try {
                return truncate(Arrays.toString(filtered.toArray()), PARAM_MAX_LEN);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
