package com.solo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC配置类
 * 配置JWT拦截器，仅放行公开登录接口；本地存储模式下另外暴露上传目录的静态资源。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final FileStorageProperties fileProps;
    private final XssStringArgumentResolver xssStringArgumentResolver;

    /**
     * 注册拦截器
     * 所有接口默认拦截，仅放行登录接口；同时放行 file.local.public-prefix 路径下的静态资源
     * （本地上传文件直接以静态资源形式对外暴露，不需 Token）
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var reg = registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/refresh");

        if (isLocalStorage()) {
            String prefix = sanitizePrefix(fileProps.getLocal().getPublicPrefix());
            reg.excludePathPatterns(prefix + "/**");
        }
    }

    /**
     * 本地存储模式下，把磁盘根目录映射成静态资源 URL 前缀。
     * 例如 file.local.public-prefix=/uploads & file.local.root=/var/solo/uploads
     * 时，URL /uploads/2026/06/16/xxx.jpg 直接返回磁盘上对应文件。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!isLocalStorage()) return;
        String prefix = sanitizePrefix(fileProps.getLocal().getPublicPrefix());
        Path root = Paths.get(fileProps.getLocal().getRoot()).toAbsolutePath().normalize();
        // ResourceHandlerRegistry 要求 location 以 file:/// 开头，且以 / 结尾
        String location = "file:///" + root.toString().replace(File.separatorChar, '/');
        if (!location.endsWith("/")) location = location + "/";
        registry.addResourceHandler(prefix + "/**").addResourceLocations(location);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(xssStringArgumentResolver);
    }

    private boolean isLocalStorage() {
        String type = fileProps.getStorage().getType();
        return type == null || type.isBlank() || "local".equalsIgnoreCase(type);
    }

    private static String sanitizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return "/uploads";
        String p = prefix.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
        return p;
    }
}
