package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 文件管理实体，对应 sys_file 表。
 */
@Data
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 上传时的原始文件名 */
    private String originalName;

    /** 存储路径名（含哈希，避免重名） */
    private String storageName;

    /** 可访问 URL */
    private String url;

    /** 扩展名（小写，不带点） */
    private String ext;

    /** Content-Type */
    private String mimeType;

    /** 字节数 */
    private Long size;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploaderId;

    private String uploaderName;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
