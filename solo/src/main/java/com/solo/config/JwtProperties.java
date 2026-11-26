package com.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    // 至少 32 字节；改密钥后，已经签出去的 token 全失效。
    private String secret;

    private long accessExpiration;

    // 滚动刷新不续这个绝对有效期。
    private long refreshExpiration;
}
