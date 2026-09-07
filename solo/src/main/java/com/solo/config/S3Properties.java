package com.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3 兼容存储配置。
 *
 * <p>通过 {@code endpoint-override} 指向任意 S3 兼容服务的访问入口，适配 MinIO / 阿里云 OSS /
 * 腾讯云 COS / 华为云 OBS / Ceph RGW / AWS S3 等。</p>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code endpoint-override}：S3 服务的访问入口 URL。必填。</li>
 *   <li>{@code region}：AWS SDK 必填字段；非 AWS 服务写任意值即可，推荐 {@code us-east-1}。
 *       阿里云 OSS 用所在 region（如 {@code cn-hangzhou}）更稳妥。</li>
 *   <li>{@code access-key} / {@code secret-key}：访问密钥</li>
 *   <li>{@code bucket}：默认桶名</li>
 *   <li>{@code public-url}：可选。前端访问的域名（CDN / 反代），空则用 endpoint 拼接 URL</li>
 *   <li>{@code path-style-access}：路径风格（{@code http://host/bucket/key}）还是虚拟主机风格
 *       （{@code http://bucket.host/key}）。默认 {@code true}，兼容 MinIO / Ceph / 自托管；
 *       AWS S3 同时支持两种，配 path-style 也能跑</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "s3")
public class S3Properties {

    /** S3 兼容服务的访问入口 URL（必填） */
    private String endpointOverride;

    /** AWS region（必填；非 AWS 写任意值，推荐 us-east-1） */
    private String region = "us-east-1";

    private String accessKey;
    private String secretKey;

    /** 默认桶 */
    private String bucket;

    /** 对外访问域名（可选）。空则用 endpoint 拼接 URL */
    private String publicUrl;

    /** 路径风格访问。默认 true 以兼容 MinIO / Ceph / 自托管 S3 */
    private boolean pathStyleAccess = true;
}
