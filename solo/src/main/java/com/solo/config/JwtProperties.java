package com.solo.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    // 至少 32 字节；改密钥后，已经签出去的 token 全失效。
    private String secret;

    private long accessExpiration;

    // 滚动刷新不续这个绝对有效期。
    private long refreshExpiration;

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置，请设置至少 32 字节的随机密钥");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字节");
        }
    }
}
