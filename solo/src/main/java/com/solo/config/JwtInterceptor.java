package com.solo.config;

import com.solo.common.utils.JwtUtils;
import com.solo.system.constant.SystemConstants;
import com.solo.system.entity.SysUser;
import com.solo.system.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT拦截器
 * 验证Token有效性，通过Redis比对登录状态
 * 未登录时统一返回401 JSON响应
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final SysUserService sysUserService;

    /**
     * 拦截请求，验证JWT Token
     * 验证通过后从Redis比对Token一致性，防止用户被踢下线
     * 验证通过后将 userId 和 username 写入请求属性，供后续权限校验使用
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String authorization = request.getHeader(SystemConstants.HEADER_AUTHORIZATION);
        String username;

        if (authorization == null || !authorization.startsWith(SystemConstants.BEARER_PREFIX)) {
            return handleNotLogin(response, "请先登录");
        }

        String token = authorization.substring(SystemConstants.TOKEN_PREFIX_LENGTH);
        try {
            username = jwtUtils.getUsernameFromToken(token);
        } catch (Exception e) {
            return handleNotLogin(response, "Token无效或已过期");
        }

        if (!jwtUtils.validateToken(token, username)) {
            return handleNotLogin(response, "Token无效或已过期");
        }

        // 从Redis获取该用户当前的Token，比对是否一致
        // 如果不一致，说明用户已重新登录或被管理员踢下线
        String cachedToken = jwtUtils.getToken(username);
        if (cachedToken == null || !cachedToken.equals(token)) {
            return handleNotLogin(response, "Token无效或已过期");
        }

        // 验证通过，写入完整用户信息到请求属性
        SysUser user = sysUserService.getByUsername(username);
        if (user == null) {
            return handleNotLogin(response, "用户不存在");
        }
        request.setAttribute("currentUserId", user.getId());
        request.setAttribute("currentUsername", user.getUsername());
        request.setAttribute("currentUser", user);

        return true;
    }

    /**
     * 未登录处理：统一返回401 JSON响应
     *
     * @param response  HTTP响应
     * @param message  提示信息
     * @return false，中断请求
     */
    private boolean handleNotLogin(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(SystemConstants.CONTENT_TYPE_JSON);
        response.getWriter().write(String.format("{\"code\":%d,\"message\":\"%s\"}", SystemConstants.HTTP_UNAUTHORIZED, message));
        return false;
    }
}
