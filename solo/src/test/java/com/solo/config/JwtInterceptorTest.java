package com.solo.config;

import com.solo.common.utils.JwtUtils;
import com.solo.system.entity.SysUser;
import com.solo.system.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtInterceptorTest {

    @Mock private JwtUtils jwtUtils;
    @Mock private SysUserService userService;

    @InjectMocks
    private JwtInterceptor interceptor;

    private SysUser user;

    @BeforeEach
    void setUp() {
        user = new SysUser();
        user.setId(7L);
        user.setUsername("admin");
        user.setStatus(1);
        user.setPwdResetRequired(0);
        when(jwtUtils.getUsernameFromToken("token")).thenReturn("admin");
        when(jwtUtils.validateToken("token", "admin")).thenReturn(true);
        when(jwtUtils.getToken("admin")).thenReturn("token");
        when(userService.getByUsername("admin")).thenReturn(user);
    }

    @Test
    void resetRequiredAccountCanOnlyReadProfileAndChangePassword() throws Exception {
        when(userService.isPasswordResetRequired(user)).thenReturn(true);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("GET", "/api/user/page"), blocked, null)).isFalse();
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(blocked.getContentAsString()).contains("请先修改初始密码");

        assertThat(interceptor.preHandle(request("GET", "/api/auth/profile"), new MockHttpServletResponse(), null))
                .isTrue();
        assertThat(interceptor.preHandle(request("POST", "/api/auth/password"), new MockHttpServletResponse(), null))
                .isTrue();
    }

    @Test
    void disabledAccountIsRejectedEvenIfCachedTokenStillExists() throws Exception {
        user.setStatus(0);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("GET", "/api/auth/profile"), response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void enabledAccountWithoutResetFlagPasses() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/auth/profile");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), null)).isTrue();
        assertThat(request.getAttribute("currentUserId")).isEqualTo(7L);
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer token");
        return request;
    }
}
