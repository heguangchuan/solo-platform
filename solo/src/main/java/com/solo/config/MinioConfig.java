package com.solo.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置（唯一存储实现，总是生效）。
 */
@Configuration
@ConditionalOnClass(MinioClient.class)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties cfg) {
        if (cfg.getEndpoint() == null || cfg.getEndpoint().isBlank()) {
            throw new IllegalStateException("minio.endpoint 不能为空");
        }
        return MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();
    }
}
