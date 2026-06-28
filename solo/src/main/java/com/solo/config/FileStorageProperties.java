package com.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储配置（仅支持 MinIO）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {

    private final Minio minio = new Minio();

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        /** 对外访问域名（可选）。空则用 endpoint 拼接 URL */
        private String publicUrl;
    }
}
