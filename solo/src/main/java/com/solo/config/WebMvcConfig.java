package com.solo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 文件存储已统一使用 MinIO，对外访问由 MinIO 自身或 CDN/反代承担，
 * 服务端不再以静态资源方式暴露本地目录。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final XssStringArgumentResolver xssStringArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/refresh",
                        // ZLMediaKit hook 由 ZLM 主动调用，无 JWT，使用 secret 自鉴权
                        "/api/hook/**",
                        // knife4j UI + OpenAPI JSON + Swagger 资源——公开访问，不要求登录
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/swagger-resources/**",
                        "/favicon.ico");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(xssStringArgumentResolver);
    }
}
