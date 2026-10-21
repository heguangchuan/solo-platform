package com.solo.aop;

import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;
import lombok.RequiredArgsConstructor;
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

/**
 * 拦截 {@link RequirePermission} 标注的方法，校验当前用户是否拥有所需权限。
 * <p>权限数据从 {@link PermissionCache} 获取；缓存刷新逻辑由业务 Service 在变更点显式触发。</p>
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionAspect {

    private final PermissionCache permissionCache;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint point, RequirePermission requirePermission) throws Throwable {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "请先登录");
        }

        List<String> userPermissions = permissionCache.get(userId);
        String[] requiredCodes = requirePermission.value();
        RequirePermission.RequireMode mode = requirePermission.mode();

        boolean hasPermission;
        if (mode == RequirePermission.RequireMode.ALL) {
            hasPermission = true;
            for (String code : requiredCodes) {
                if (!userPermissions.contains(code)) {
                    hasPermission = false;
                    break;
                }
            }
        } else {
            hasPermission = Arrays.stream(requiredCodes).anyMatch(userPermissions::contains);
        }

        if (!hasPermission) {
            throw new ServiceException(SystemConstants.HTTP_FORBIDDEN, "没有权限执行此操作");
        }
        return point.proceed();
    }

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
}
