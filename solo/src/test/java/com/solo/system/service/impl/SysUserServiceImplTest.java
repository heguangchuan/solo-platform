package com.solo.system.service.impl;

import com.solo.common.ServiceException;
import com.solo.common.utils.JwtUtils;
import com.solo.common.utils.PasswordUtils;
import com.solo.system.constant.SystemConstants;
import com.solo.system.entity.SysUser;
import com.solo.system.request.UserRequest;
import com.solo.system.response.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private JwtUtils jwtUtils;

    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.spy(new SysUserServiceImpl(jwtUtils));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void legacyAdminLoginUpgradesHashAndKeepsForcedReset() {
        SysUser user = user(7L, "admin", SystemConstants.DEFAULT_PASSWORD);
        user.setPwdResetRequired(0);
        doReturn(user).when(service).getByUsername("admin");
        doReturn(true).when(service).updateById(user);
        when(jwtUtils.generateToken("admin")).thenReturn("access");
        when(jwtUtils.generateRefreshToken("admin")).thenReturn("refresh");

        LoginResponse response = service.login("admin", "admin123");

        assertThat(PasswordUtils.isLegacyMd5(user.getPassword())).isFalse();
        assertThat(PasswordUtils.matches("admin123", user.getPassword())).isTrue();
        assertThat(user.getPwdResetRequired()).isEqualTo(1);
        assertThat(response.getUser().getPwdResetRequired()).isEqualTo(1);
        verify(service).updateById(user);
        verify(jwtUtils).saveToken("admin", "access");
        verify(jwtUtils).saveRefreshToken("admin", "refresh");
    }

    @Test
    void wrongPasswordDoesNotMigrateOrIssueTokens() {
        SysUser user = user(7L, "admin", SystemConstants.DEFAULT_PASSWORD);
        doReturn(user).when(service).getByUsername("admin");

        assertThatThrownBy(() -> service.login("admin", "bad-password"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("用户名或密码错误");

        verify(service, never()).updateById(any());
        verifyNoInteractions(jwtUtils);
    }

    @Test
    void createUserUsesProvidedPasswordInsteadOfPredictableDefault() {
        UserRequest request = new UserRequest();
        request.setUsername("reader");
        request.setInitialPassword("Reader123");
        doReturn(null).when(service).getByUsername("reader");
        doReturn(true).when(service).save(any(SysUser.class));

        service.createUser(request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(service).save(captor.capture());
        SysUser created = captor.getValue();
        assertThat(PasswordUtils.matches("Reader123", created.getPassword())).isTrue();
        assertThat(PasswordUtils.matches("reader@123456", created.getPassword())).isFalse();
        assertThat(created.getPwdResetRequired()).isEqualTo(1);
    }

    @Test
    void changePasswordRevokesAccessAndRefreshTokens() {
        SysUser user = user(7L, "admin", PasswordUtils.encode("OldPass123"));
        doReturn(user).when(service).getById(7L);
        doReturn(true).when(service).updateById(user);

        service.changePassword(7L, "OldPass123", "NewPass123");

        assertThat(PasswordUtils.matches("NewPass123", user.getPassword())).isTrue();
        assertThat(user.getPwdResetRequired()).isEqualTo(0);
        verify(jwtUtils).removeToken("admin");
    }

    @Test
    void currentAccountCannotDisableItself() {
        SysUser user = user(7L, "admin", PasswordUtils.encode("OldPass123"));
        doReturn(user).when(service).getById(7L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(() -> service.switchStatus(7L, 0))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不能禁用或删除当前登录账号");

        verify(service, never()).updateById(any());
        verifyNoInteractions(jwtUtils);
    }

    private static SysUser user(Long id, String username, String password) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setStatus(1);
        user.setPwdResetRequired(0);
        return user;
    }
}
