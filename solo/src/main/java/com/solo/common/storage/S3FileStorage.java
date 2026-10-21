package com.solo.common.storage;

import com.solo.common.ServiceException;
import com.solo.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;

/**
 * S3 兼容存储实现（唯一存储实现）。
 *
 * <p>通过 {@code S3Properties.endpoint-override} 适配 MinIO / 阿里云 OSS / 腾讯云 COS /
 * 华为云 OBS / Ceph RGW / AWS S3 等任意 S3 兼容服务。</p>
 *
 * <h3>URL 拼接规则</h3>
 * <ul>
 *   <li>配置了 {@code s3.public-url} 则用其作为前缀（适合走 CDN / 反代）</li>
 *   <li>否则使用 {@code s3.endpoint-override}（适合开发环境直连）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {

    private static final int DEFAULT_PRESIGNED_EXPIRY_SEC = 3600;

    /** us-east-1 是 AWS SDK 的默认 region，也是唯一不需要 LocationConstraint 的 region */
    private static final String US_EAST_1 = "us-east-1";

    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3Properties props;

    @jakarta.annotation.PostConstruct
    public void ensureBucket() {
        String bucket = bucket();
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            try {
                CreateBucketRequest.Builder cb = CreateBucketRequest.builder().bucket(bucket);
                // 非 us-east-1 region 需要 CreateBucketConfiguration 带 LocationConstraint
                if (!US_EAST_1.equals(props.getRegion())) {
                    cb.createBucketConfiguration(CreateBucketConfiguration.builder()
                            .locationConstraint(BucketLocationConstraint.fromValue(props.getRegion()))
                            .build());
                }
                s3.createBucket(cb.build());
                log.info("[S3存储] 创建 bucket: {} region: {}", bucket, props.getRegion());
            } catch (Exception createErr) {
                // 部分 S3 兼容服务（MinIO 等）拒收 LocationConstraint；重试一次不带 config
                try {
                    s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                    log.info("[S3存储] 创建 bucket（不带 LocationConstraint）: {}", bucket);
                } catch (Exception retryErr) {
                    log.warn("[S3存储] 创建 bucket 失败 bucket={} 错误:{}",
                            bucket, retryErr.getMessage());
                }
            }
        } catch (S3Exception e) {
            int code = e.statusCode();
            // 老 MinIO（< 2023）对不存在的 bucket HEAD 时返 403（防信息泄露），
            // 或凭证无 ListBucket 权限。这两种都不影响后续上传——运行时 putObject
            // 会自带相应错误码，调用方按需重试或报错。启动期降级为 INFO 跳过自检。
            if (code == 403 || code == 404) {
                log.info("[S3存储] 跳过 bucket 自检 bucket={} 状态码:{} 原因:{}",
                        bucket, code, e.getMessage());
                return;
            }
            log.warn("[S3存储] 检查 bucket 失败 bucket={} 状态码:{} 错误:{}",
                    bucket, code, e.getMessage());
        } catch (Exception e) {
            // 启动时不打断应用：bucket 也许已经存在但接口偶发失败；运行时再操作会重新检测。
            log.warn("[S3存储] 检查 bucket 异常 bucket={} 错误:{}",
                    bucket, e.getMessage());
        }
    }

    @Override
    public String upload(MultipartFile file, String storageName) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String bucket = bucket();
        String contentType = file.getContentType() == null
                ? "application/octet-stream" : file.getContentType();
        try (InputStream in = file.getInputStream()) {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageName)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(in, file.getSize()));
        } catch (Exception e) {
            log.error("[S3存储] 上传失败 bucket={} object={}", bucket, storageName, e);
            throw new ServiceException("文件上传失败：" + e.getMessage());
        }
        return buildPublicUrl(bucket, storageName);
    }

    @Override
    public String upload(InputStream in, long size, String contentType, String storageName) {
        if (in == null) {
            throw new ServiceException("上传流不能为空");
        }
        String bucket = bucket();
        String ct = (contentType == null || contentType.isBlank())
                ? "application/octet-stream" : contentType;
        try {
            PutObjectRequest.Builder put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageName)
                    .contentType(ct);
            RequestBody body;
            if (size >= 0) {
                put.contentLength(size);
                body = RequestBody.fromInputStream(in, size);
            } else {
                // 未知长度：走 ContentStreamProvider，让 AWS SDK 按需 chunked 上传
                body = RequestBody.fromContentProvider(() -> in, -1L, ct);
            }
            s3.putObject(put.build(), body);
            log.info("[S3存储] 流式上传成功 bucket={} object={} size={}", bucket, storageName, size);
        } catch (Exception e) {
            log.error("[S3存储] 流式上传失败 bucket={} object={}", bucket, storageName, e);
            throw new ServiceException("文件上传失败：" + e.getMessage());
        }
        return storageName;
    }

    @Override
    public InputStream openStream(String storageName) {
        try {
            return s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket())
                    .key(storageName)
                    .build());
        } catch (Exception e) {
            throw new ServiceException("读取文件失败：" + e.getMessage());
        }
    }

    @Override
    public String presignedUrl(String storageName, int expirySeconds) {
        if (storageName == null || storageName.isBlank()) {
            throw new ServiceException("storageName 不能为空");
        }
        int expiry = expirySeconds > 0 ? expirySeconds : DEFAULT_PRESIGNED_EXPIRY_SEC;
        try {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(expiry))
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(bucket())
                                    .key(storageName)
                                    .build())
                            .build());
            return presigned.url().toString();
        } catch (Exception e) {
            log.error("[S3存储] 生成预签名 URL 失败 object={} 错误:{}",
                    storageName, e.getMessage());
            throw new ServiceException("生成预签名 URL 失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String storageName) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket())
                    .key(storageName)
                    .build());
        } catch (S3Exception e) {
            // 删除失败不抛异常：DB 已软删，存储侧字节由后台清理任务兜底
            log.warn("[S3存储] 删除失败 object={} 状态码:{} 错误:{}",
                    storageName, e.statusCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("[S3存储] 删除失败 object={} 错误:{}",
                    storageName, e.getMessage());
        }
    }

    /* ============================== 内部 ============================== */

    private String bucket() {
        String b = props.getBucket();
        if (b == null || b.isBlank()) throw new IllegalStateException("s3.bucket 未配置");
        return b;
    }

    /**
     * 拼接对外可访问的 URL。
     * 配置了 public-url 时用 public-url（适合 CDN / 反代），否则用 endpoint-override。
     */
    private String buildPublicUrl(String bucket, String object) {
        String base = props.getPublicUrl();
        if (base == null || base.isBlank()) base = props.getEndpointOverride();
        if (base == null) return "/" + bucket + "/" + object;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + bucket + "/" + object;
    }
}
