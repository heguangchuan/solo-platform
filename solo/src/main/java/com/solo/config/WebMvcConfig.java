package com.solo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置类。
 * 配置 JWT 拦截器，仅放行公开登录 / 刷新接口；注册 XSS 字符串参数解析器。
 *
 * <p>文件存储已统一使用 MinIO，对外访问由 MinIO 自身或 CDN/反代承担，
 * 服务端不再以静态资源方式暴露本地目录。</p>
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
                        // smart-doc 生成的 API 文档——公开访问，不要求登录
                        "/sdoc/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(xssStringArgumentResolver);
    }
}
