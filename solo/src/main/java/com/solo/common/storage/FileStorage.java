package com.solo.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * <p>底层走 S3 兼容协议（AWS SDK v2），通过 {@code s3.endpoint-override} 适配 MinIO /
 * 阿里云 OSS / 腾讯云 COS / 华为云 OBS / Ceph RGW / AWS S3 等任意 S3 兼容服务。
 * 所有文件都存到配置指定的桶里。</p>
 */
public interface FileStorage {

    String upload(MultipartFile file, String storageName);

    String upload(InputStream in, long size, String contentType, String storageName);

    InputStream openStream(String storageName);

    String presignedUrl(String storageName, int expirySeconds);

    void delete(String storageName);
}
