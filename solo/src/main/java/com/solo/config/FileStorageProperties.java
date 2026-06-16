package com.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储配置：通过 {@code file.storage.type} 区分 local / minio。
 *
 * <p>用 yml/properties 控制启用哪个 {@link com.solo.service.storage.FileStorage} 实现，
 * 不写 file.storage.type 时默认 local。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {

    private final Storage storage = new Storage();
    private final Local local = new Local();
    private final Minio minio = new Minio();

    @Data
    public static class Storage {
        /** local / minio */
        private String type = "local";
    }

    @Data
    public static class Local {
        /** 本地根目录（绝对路径），所有上传文件都落到此目录下 */
        private String root = "./uploads";
        /** 对外访问前缀（与下面 WebMvcConfig 中的静态资源映射保持一致） */
        private String publicPrefix = "/uploads";
    }

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        /** 对外访问域名（可选）。空则用 endpoint 拼接 URL */
        private String publicUrl;
    }
}
