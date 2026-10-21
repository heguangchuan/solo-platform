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

@Schema(description = "上级平台共享通道")
@Data
@TableName("gb_platform_share")
public class GbPlatformShare {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "上级平台主键（逻辑关联 gb_parent_platform.id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long platformId;

    @Schema(description = "所属推送分组主键（逻辑关联 gb_platform_group.id；UI 强制先在弹窗左侧选中分组才能挂载，同一通道在本平台只能落在一个分组下）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    @Schema(description = "通道主键（逻辑关联 gb_device_channel.id 中 parental=0 的行）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resourceId;

    @Schema(description = "对上级暴露的 20 位国标码（撞码时分配，不撞码时为 NULL 表示透传 channel_id；本平台命名空间内唯一，上级 INVITE 按此码反查真实通道，同一通道对所有上级复用同一 exposed_code；撞码判定是平台级而非分组级）")
    private String exposedCode;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
