package com.solo.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储抽象。
 *
 * <p>当前默认使用 {@link com.solo.service.storage.LocalFileStorage}，后续切换 MinIO / OSS / S3 时业务代码无感知。
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
     * 打开文件读取流（供下载 / 预览使用）。
     */
    InputStream openStream(String storageName);

    /**
     * 删除存储侧的文件本体。失败不应影响数据库逻辑删除。
     */
    void delete(String storageName);
}
