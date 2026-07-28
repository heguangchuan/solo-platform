package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 上级平台共享目录
 * <p>本平台作为下级向上级共享资源目录时，选中的分组（目录）记录。
 * 一行 = 一个上级平台 × 一个共享目录分组，无物理外键（ADR-011）。</p>
 */
@Data
@TableName("gb_platform_catalog")
public class GbPlatformCatalog {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 上级平台主键（逻辑关联 gb_parent_platform.id）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long platformId;

    /**
     * 分组主键（逻辑关联 gb_group.id）
     */
    private String groupId;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
