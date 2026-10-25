package com.solo.openapi.config;

import com.solo.openapi.auth.OpenApiScopeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 开放 API 拦截器注册。签名鉴权在 Filter 中完成（需要先缓存 body），
 * 这里只挂 Scope 拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiWebMvcConfig implements WebMvcConfigurer {

    private final OpenApiScopeInterceptor openApiScopeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openApiScopeInterceptor)
                .addPathPatterns("/open-api/**")
                .order(0);
    }
}
