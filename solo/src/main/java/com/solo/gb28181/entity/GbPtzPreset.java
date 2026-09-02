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

/**
 * 云台预置位
 * <p>每个通道可以配置多个预置位，保存角度记忆，一键调用转到预设位置。</p>
 */
@Schema(description = "云台预置位")
@Data
@TableName("gb_ptz_preset")
public class GbPtzPreset {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 通道数据库主键（gb_device_channel.id）
     */
    @Schema(description = "通道数据库主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long channelDbId;

    /**
     * 预置位编号（设备侧编号，从 1 开始）
     */
    @Schema(description = "预置位编号")
    private Integer presetId;

    /**
     * 预置位名称（如"大门入口"）
     */
    @Schema(description = "预置位名称")
    private String name;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
