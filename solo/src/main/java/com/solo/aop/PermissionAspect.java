package com.solo.aop;

import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;
import com.solo.system.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限验证切面
 * 拦截标注了 @RequirePermission 注解的方法，验证用户是否拥有所需权限
 */
@Aspect
@Component
@Slf4j
public class PermissionAspect {

    /**
     * 用户权限缓存：key=userId, value=权限编码列表
     */
    private static final Map<Long, List<String>> PERMISSION_CACHE = new ConcurrentHashMap<>();

    private final SysUserService sysUserService;

    public PermissionAspect(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 环绕通知：拦截标注了 @RequirePermission 的方法
     */
    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint point, RequirePermission requirePermission) throws Throwable {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "请先登录");
        }

        List<String> userPermissions = getUserPermissions(userId);

        String[] requiredCodes = requirePermission.value();
        RequirePermission.RequireMode mode = requirePermission.mode();

        boolean hasPermission;
        if (mode == RequirePermission.RequireMode.ALL) {
            // 需要拥有所有指定权限
            hasPermission = true;
            for (String code : requiredCodes) {
                if (!userPermissions.contains(code)) {
                    hasPermission = false;
                    break;
                }
            }
        } else {
            // 需要拥有任意一个指定权限
            hasPermission = Arrays.stream(requiredCodes)
                    .anyMatch(userPermissions::contains);
        }

        if (!hasPermission) {
            throw new ServiceException(SystemConstants.HTTP_FORBIDDEN, "没有权限执行此操作");
        }

        return point.proceed();
    }

    /**
     * 从当前请求中获取 userId
     */
    private Long getCurrentUserId() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
                Object id = request.getAttribute("currentUserId");
                return id instanceof Long ? (Long) id : null;
            }
        } catch (Exception e) {
            log.warn("获取当前用户ID失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取用户权限编码列表（带缓存）
     */
    private List<String> getUserPermissions(Long userId) {
        return PERMISSION_CACHE.computeIfAbsent(userId, id -> {
            try {
                return sysUserService.getPermissionCodes(id);
            } catch (Exception e) {
                log.error("获取用户权限失败: userId={}", id, e);
                return List.of();
            }
        });
    }

    /**
     * 清除指定用户的权限缓存
     */
    public void clearPermissionCache(Long userId) {
        PERMISSION_CACHE.remove(userId);
    }
}
