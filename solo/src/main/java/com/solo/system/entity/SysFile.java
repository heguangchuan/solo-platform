package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 文件管理实体，对应 sys_file 表。
 */
@Schema(description = "文件")
@Data
@TableName("sys_file")
public class SysFile {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 上传时的原始文件名 */
    @Schema(description = "原始文件名")
    private String originalName;

    /** 存储路径名（含哈希，避免重名） */
    @Schema(description = "存储路径名")
    private String storageName;

    /** 可访问 URL */
    @Schema(description = "URL")
    private String url;

    /** 扩展名（小写，不带点） */
    @Schema(description = "扩展名")
    private String ext;

    /** Content-Type */
    @Schema(description = "MIME 类型")
    private String mimeType;

    /** 字节数 */
    @Schema(description = "字节数")
    private Long size;

    @Schema(description = "上传人 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploaderId;

    @Schema(description = "上传人名称")
    private String uploaderName;

    @Schema(description = "逻辑删除（0=未删，1=已删）")
    @TableLogic
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
