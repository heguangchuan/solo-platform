package com.solo.service.storage;

import com.solo.common.ServiceException;
import com.solo.config.FileStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * MinIO 文件存储实现（唯一存储实现）。
 *
 * <p>URL 拼接规则：
 * <ul>
 *   <li>配置了 {@code file.minio.public-url} 则用其作为前缀（适合走 CDN / 反代）</li>
 *   <li>否则使用 {@code file.minio.endpoint}（适合开发环境直连）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioFileStorage implements FileStorage {

    private final MinioClient minioClient;
    private final FileStorageProperties props;

    /** 启动时确保 bucket 存在 */
    @jakarta.annotation.PostConstruct
    public void ensureBucket() {
        String bucket = bucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[MinIO] 创建 bucket: {}", bucket);
            }
        } catch (Exception e) {
            // 启动时不打断应用：bucket 也许已经存在但接口偶发失败；运行时再操作会重新检测。
            log.warn("[MinIO] 检查/创建 bucket 失败，可能影响后续上传：{}", e.getMessage());
        }
    }

    @Override
    public String upload(MultipartFile file, String storageName) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String bucket = bucket();
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageName)
                            .stream(in, file.getSize(), -1)
                            .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            log.error("[MinIO] 上传失败 bucket={} object={}", bucket, storageName, e);
            throw new ServiceException("文件上传失败：" + e.getMessage());
        }
        return buildPublicUrl(bucket, storageName);
    }

    @Override
    public InputStream openStream(String storageName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket())
                            .object(storageName)
                            .build()
            );
        } catch (Exception e) {
            throw new ServiceException("读取文件失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String storageName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket())
                            .object(storageName)
                            .build()
            );
        } catch (Exception e) {
            // 删除失败不抛异常：DB 已软删，存储侧字节由后台清理任务兜底
            log.warn("[MinIO] 删除失败 object={}：{}", storageName, e.getMessage());
        }
    }

    /* ============================== 内部 ============================== */

    private String bucket() {
        String b = props.getMinio().getBucket();
        if (b == null || b.isBlank()) throw new IllegalStateException("file.minio.bucket 未配置");
        return b;
    }

    /**
     * 拼接对外可访问的 URL。
     * 配置了 public-url 时用 public-url（适合 CDN / 反代），否则用 endpoint。
     */
    private String buildPublicUrl(String bucket, String object) {
        String base = props.getMinio().getPublicUrl();
        if (base == null || base.isBlank()) base = props.getMinio().getEndpoint();
        if (base == null) return "/" + bucket + "/" + object;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + bucket + "/" + object;
    }
}
