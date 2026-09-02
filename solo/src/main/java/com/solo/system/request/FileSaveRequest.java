package com.solo.system.request;

import lombok.Data;

/**
 * 文件元数据登记请求
 *
 * <p>对应接口 {@code POST /api/file/meta}，仅落库元数据不传字节。
 * 适用场景：前端 / 独立服务已通过其他渠道把字节传到对象存储，只调本接口同步元信息到本系统。</p>
 */
@Data
public class FileSaveRequest {

    /** 原始文件名（必填，用户侧可见名） */
    private String originalName;

    /** 存储文件名（必填，对象存储里的对象 key） */
    private String storageName;

    /** 可访问 URL（必填，对象存储的访问地址） */
    private String url;

    /** 扩展名（可选，不含点，如 {@code "png"}） */
    private String ext;

    /** MIME 类型（可选，如 {@code "image/png"}） */
    private String mimeType;

    /** 文件大小（必填，字节） */
    private Long size;
}
