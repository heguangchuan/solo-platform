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

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final SysUserService sysUserService;

    /**
     * 验证 JWT，并与 Redis 中的当前 token 比对，重新登录后旧 token 立即失效。
     * 验证通过后将用户身份写入请求属性。
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

        // 同一账号在别处重新登录后，旧 token 不再接受
        String cachedToken = jwtUtils.getToken(username);
        if (cachedToken == null || !cachedToken.equals(token)) {
            return handleNotLogin(response, "Token无效或已过期");
        }

        // 验证通过，写入完整用户信息到请求属性
        SysUser user = sysUserService.getByUsername(username);
        if (user == null) {
            return handleNotLogin(response, "用户不存在");
        }
        if (!Integer.valueOf(SystemConstants.STATUS_ENABLED).equals(user.getStatus())) {
            return handleNotLogin(response, "账号已被禁用");
        }
        if (sysUserService.isPasswordResetRequired(user) && !isPasswordResetEndpoint(request)) {
            return handleResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    SystemConstants.HTTP_FORBIDDEN, "请先修改初始密码");
        }
        request.setAttribute("currentUserId", user.getId());
        request.setAttribute("currentUsername", user.getUsername());
        request.setAttribute("currentUser", user);

        return true;
    }

    private boolean isPasswordResetEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ("POST".equals(request.getMethod()) && "/api/auth/password".equals(path))
                || ("GET".equals(request.getMethod()) && "/api/auth/profile".equals(path));
    }

    private boolean handleNotLogin(HttpServletResponse response, String message) throws Exception {
        return handleResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                SystemConstants.HTTP_UNAUTHORIZED, message);
    }

    private boolean handleResponse(HttpServletResponse response, int status, int code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(SystemConstants.CONTENT_TYPE_JSON);
        response.getWriter().write(String.format("{\"code\":%d,\"message\":\"%s\"}", code, message));
        return false;
    }
}
