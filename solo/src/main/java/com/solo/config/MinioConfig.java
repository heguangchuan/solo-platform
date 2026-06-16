package com.solo.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 *
 * <p>仅在以下两个条件同时满足时生效：
 * <ul>
 *   <li>classpath 上存在 io.minio:minio（即依赖被引入）</li>
 *   <li>{@code file.storage.type=minio}</li>
 * </ul>
 * 任一不满足时整个配置类被跳过，{@link MinioClient} 也不会注册——
 * 因此即便不引入 minio 依赖，应用启动也不会因 NoClassDefFoundError 失败。
 */
@Configuration
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "minio")
public class MinioConfig {

    @Bean
    public MinioClient minioClient(FileStorageProperties props) {
        FileStorageProperties.Minio cfg = props.getMinio();
        if (cfg.getEndpoint() == null || cfg.getEndpoint().isBlank()) {
            throw new IllegalStateException("file.minio.endpoint 不能为空");
        }
        return MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();
    }
}
