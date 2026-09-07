package com.solo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 兼容存储客户端配置。
 *
 * <p>注册两个 bean：</p>
 * <ul>
 *   <li>{@link S3Client}：常规读写（上传、下载、删除、桶管理）</li>
 *   <li>{@link S3Presigner}：生成预签名 URL（与 {@link S3Client} 解耦，生命周期独立）</li>
 * </ul>
 *
 * <h3>兼容性配置</h3>
 * <ul>
 *   <li>默认启用 path-style：MinIO / Ceph / 自托管 S3 必须 path-style；阿里云 / 腾讯云 / 华为云
 *       两种都支持；AWS S3 也兼容。统一开 path-style 减少配置分支</li>
 *   <li>request/response checksum 设成 {@code WHEN_REQUIRED}：避开老版本 MinIO / 部分阿里云 OSS 模式
 *       拒收 SHA256 校验的问题（AWS SDK 默认会算 SHA256）</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnClass(S3Client.class)
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3Properties cfg) {
        if (!StringUtils.hasText(cfg.getEndpointOverride())) {
            throw new IllegalStateException("s3.endpoint-override 不能为空");
        }
        log.info("[S3存储] 初始化 S3Client endpoint={} region={} pathStyle={}",
                cfg.getEndpointOverride(), cfg.getRegion(), cfg.isPathStyleAccess());
        return S3Client.builder()
                .region(Region.of(cfg.getRegion()))
                .endpointOverride(URI.create(cfg.getEndpointOverride()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(cfg.isPathStyleAccess())
                        .build())
                // 兼容老版本 MinIO / 部分阿里云 OSS 模式：默认会算 SHA256，老实现会拒收
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(S3Properties cfg) {
        if (!StringUtils.hasText(cfg.getEndpointOverride())) {
            throw new IllegalStateException("s3.endpoint-override 不能为空");
        }
        return S3Presigner.builder()
                .region(Region.of(cfg.getRegion()))
                .endpointOverride(URI.create(cfg.getEndpointOverride()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(cfg.isPathStyleAccess())
                        .build())
                .build();
    }
}
