package com.solo.aop;

import com.solo.openapi.auth.OpenApiClientIpResolver;
import com.solo.system.controller.AuthController;
import com.solo.system.request.LoginRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterAspectTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private OpenApiClientIpResolver clientIpResolver;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature signature;

    @InjectMocks
    private RateLimiterAspect aspect;

    @Test
    void loginLimitKeyContainsUsernameAndTrustedClientIp() throws Throwable {
        Method method = AuthController.class.getMethod("login", LoginRequest.class);
        LoginRequest login = new LoginRequest();
        login.setUsername("Admin");
        login.setPassword("password");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{login});
        when(clientIpResolver.resolve(null)).thenReturn("127.0.0.1");
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("return 0");
        script.setResultType(Long.class);
        ReflectionTestUtils.setField(aspect, "rateLimitScript", script);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(),
                any(), any(), any(), any(), any())).thenReturn(0L);
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.around(joinPoint)).isEqualTo("ok");

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keys.capture(),
                any(), any(), any(), any(), any());
        assertThat(keys.getValue().get(0).toString())
                .contains(":subject:admin:ip:127.0.0.1");
    }
}
