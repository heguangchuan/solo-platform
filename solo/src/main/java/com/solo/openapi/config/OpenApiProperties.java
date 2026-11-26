package com.solo.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openapi")
public class OpenApiProperties {

    private boolean enabled = true;

    // 生产环境必须换，默认密钥只能本地玩。
    // Base64 编码的 32 字节串：0123456789abcdef0123456789abcdef
    private String secretKey = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private int oldSecretGraceHours = 24;

    private boolean auditLogEnabled = true;

    // 只有 TCP 直连地址命中可信代理，才信 X-Forwarded-For/X-Real-IP。
    // 直连部署留空；nginx 和应用同机就填 127.0.0.1,::1。
    private String trustedProxies = "";
}
