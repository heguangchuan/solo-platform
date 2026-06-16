package com.solo.service.storage;

import com.solo.common.ServiceException;
import com.solo.config.FileStorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件存储实现：把字节落到磁盘，并暴露 URL（依赖 WebMvcConfig 的静态资源映射）。
 *
 * <p>启用条件：{@code file.storage.type=local} 或缺省。与 {@link MinioFileStorage} 互斥。
 *
 * <p>磁盘布局：{@code <root>/<storageName>}，其中 storageName 由 service 生成（如 2026/06/16/{uuid}.jpg）。
 *
 * <p>对外 URL：{@code <publicPrefix>/<storageName>}，前端通过 Spring 静态资源处理器直接拉取。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

    private final FileStorageProperties props;

    /** 启动时确保根目录存在 */
    @PostConstruct
    public void ensureRoot() {
        Path root = root();
        try {
            Files.createDirectories(root);
            log.info("[LocalFileStorage] 存储根目录：{}", root);
        } catch (IOException e) {
            log.warn("[LocalFileStorage] 创建根目录失败：{}", e.getMessage());
        }
    }

    @Override
    public String upload(MultipartFile file, String storageName) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        Path target = resolveSafe(storageName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("[LocalFileStorage] 写入失败 path={}", target, e);
            throw new ServiceException("文件写入失败：" + e.getMessage());
        }
        return buildUrl(storageName);
    }

    @Override
    public InputStream openStream(String storageName) {
        Path target = resolveSafe(storageName);
        if (!Files.exists(target)) {
            throw new ServiceException("文件不存在");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new ServiceException("读取文件失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String storageName) {
        try {
            Path target = resolveSafe(storageName);
            Files.deleteIfExists(target);
        } catch (Exception e) {
            // 删除失败不抛：DB 已软删，存储侧字节由清理任务兜底
            log.warn("[LocalFileStorage] 删除失败 storageName={}：{}", storageName, e.getMessage());
        }
    }

    /* ============================== 内部工具 ============================== */

    private Path root() {
        String configured = props.getLocal().getRoot();
        if (configured == null || configured.isBlank()) configured = "./uploads";
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    /**
     * 安全拼接磁盘路径：
     * 1. 拒绝绝对路径 / 含 ".." 的相对段；
     * 2. normalize 后必须仍位于 root 之下，防越界。
     */
    private Path resolveSafe(String storageName) {
        if (storageName == null || storageName.isBlank()) {
            throw new ServiceException("存储路径名不能为空");
        }
        // 统一分隔符
        String normalized = storageName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new ServiceException("非法的存储路径：" + storageName);
        }
        Path root = root();
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw new ServiceException("非法的存储路径：" + storageName);
        }
        return target;
    }

    /** 拼接对外 URL：{publicPrefix}/{storageName}，URL 永远用正斜杠 */
    private String buildUrl(String storageName) {
        String prefix = props.getLocal().getPublicPrefix();
        if (prefix == null || prefix.isBlank()) prefix = "/uploads";
        if (!prefix.startsWith("/")) prefix = "/" + prefix;
        if (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        String relative = storageName.replace('\\', '/');
        if (relative.startsWith("/")) relative = relative.substring(1);
        return prefix + "/" + relative;
    }
}
