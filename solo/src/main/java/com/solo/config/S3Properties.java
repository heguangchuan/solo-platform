package com.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


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

    private String bucket;

    /** 对外访问域名（可选）。空则用 endpoint 拼接 URL */
    private String publicUrl;

    /** 路径风格访问。默认 true 以兼容 MinIO / Ceph / 自托管 S3 */
    private boolean pathStyleAccess = true;
}
