package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(description = "上级平台推送目录分组")
@Data
@TableName("gb_platform_group")
public class GbPlatformGroup {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "分组名称")
    private String name;

    @Schema(description = "分组国标编码（GB/T 28181-2016 附录 D 20 位数字：前 10 位为本平台 domain，第 11-13 位为业务分类 215=业务分组 / 216=虚拟组织，后 7 位为序号；平台内唯一 uk_platform_code）")
    private String code;

    @Schema(description = "所属上级平台主键（逻辑关联 gb_parent_platform.id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long platformId;

    @Schema(description = "父分组主键（引用同表 id；根节点 = 0）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
