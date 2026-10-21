package com.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <p>字段命名走 Spring Boot RelaxedBinding：YAML 的 kebab-case
 * （如 {@code access-expiration}）会自动绑定到 camelCase 字段（{@code accessExpiration}）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 签名密钥（Base64 编码后的 HMAC-SHA 密钥原文，JJWT 会按字节解析）。
     * <p>至少 32 字节才能满足 HS256 强度要求；改值后所有已签发 token 立即失效。</p>
     */
    private String secret;

    /**
     * access token 有效期（毫秒）。默认 30 分钟。
     */
    private long accessExpiration;

    /**
     * refresh token 有效期（毫秒）。默认 7 天。
     * <p>滚动刷新时不会延长，只决定 refresh token 的绝对失效时间。</p>
     */
    private long refreshExpiration;
}
