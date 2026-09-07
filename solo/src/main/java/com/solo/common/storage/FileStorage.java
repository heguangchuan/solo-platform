package com.solo.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储抽象。
 *
 * <p>底层走 S3 兼容协议（AWS SDK v2），通过 {@code s3.endpoint-override} 适配 MinIO /
 * 阿里云 OSS / 腾讯云 COS / 华为云 OBS / Ceph RGW / AWS S3 等任意 S3 兼容服务。
 * 所有文件都存到配置指定的桶里。</p>
 */
public interface FileStorage {

    /**
     * 上传文件。
     *
     * @param file        前端 multipart 文件
     * @param storageName 服务端期望的"存储相对路径"，例如 {@code 2026/06/16/{hash}.jpg}
     * @return 上传后用于访问的 URL（可能是相对路径，由 controller 拼接成可访问链接）
     */
    String upload(MultipartFile file, String storageName);

    /**
     * 上传字节流（不带 multipart 包装，供后端内部流转：例如 ZLM 录像 MP4 直接落库）。
     *
     * @param in          输入流（由调用方负责关闭，方法内部不关）
     * @param size        字节数（-1 未知；走 chunked 上传）
     * @param contentType MIME，例如 {@code video/mp4}
     * @param storageName 存储相对路径
     * @return 上传后的对象名（{@code storageName} 原样返回，便于调用方记到业务对象里）
     */
    String upload(InputStream in, long size, String contentType, String storageName);

    /**
     * 打开文件读取流（供下载 / 预览使用）。
     * <p>调用方负责关闭流，关闭时自动释放底层 HTTP 连接。</p>
     */
    InputStream openStream(String storageName);

    /**
     * 生成预签名下载 URL（临时、有有效期）。
     * <p>录像下载场景用：MP4 落 S3 兼容存储后给前端一个临时 URL，避免直接把对象路径暴露。
     * 默认 1 小时有效期，到期重新签发或改走 controller 流式下载。</p>
     *
     * @param storageName   存储相对路径
     * @param expirySeconds 有效期秒数（&lt;=0 用默认 3600）
     * @return 可下载的完整 URL
     */
    String presignedUrl(String storageName, int expirySeconds);

    /**
     * 删除存储侧的文件本体。失败不应影响数据库逻辑删除。
     */
    void delete(String storageName);
}
