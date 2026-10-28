package com.solo.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 开放 API 配置（openapi.* 前缀）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "openapi")
public class OpenApiProperties {

    /** 是否启用开放 API */
    private boolean enabled = true;

    /**
     * 应用密钥/回调密钥的 AES 加密主密钥（Base64，32 字节）。
     * 生产环境务必通过配置覆盖；默认值仅供本地开发。
     */
    // Base64 编码的 32 字节串：0123456789abcdef0123456789abcdef
    private String secretKey = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    /** 密钥换钥后旧密钥的过渡期（小时） */
    private int oldSecretGraceHours = 24;

    /** 审计日志开关（高 QPS 场景可关） */
    private boolean auditLogEnabled = true;

    /**
     * 可信反向代理地址（逗号分隔 IP/CIDR）。
     * <p>只有直连地址命中这里时才采信 X-Forwarded-For / X-Real-IP；为空表示不采信任何代理头，
     * 客户端 IP 取 TCP 源地址。直连部署留空，nginx 与应用同机填 127.0.0.1,::1。
     */
    private String trustedProxies = "";
}
