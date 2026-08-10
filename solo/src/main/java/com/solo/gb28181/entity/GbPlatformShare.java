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
 * 上级平台共享资源（目录 + 通道合一）
 * <p>本平台作为下级向上级共享资源时，选中的分组/通道记录。
 * 一行 = 一个上级平台 × 一个资源节点，{@link #type} 区分 group / channel，无物理外键（ADR-011）。</p>
 * <p>资源数据源：{@link GbGroup#getId()}（业务短码，雪花主键）+ {@link GbDeviceChannel#getId()}。
 * 不另设合并表，读模型走 {@code CatalogItemMapper} UNION。</p>
 */
@Data
@TableName("gb_platform_share")
public class GbPlatformShare {

    /** 资源类型：分组 */
    public static final String TYPE_GROUP = "group";
    /** 资源类型：通道 */
    public static final String TYPE_CHANNEL = "channel";

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 上级平台主键（逻辑关联 gb_parent_platform.id）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long platformId;

    /**
     * 资源主键：group→gb_group.id / channel→gb_device_channel.id（逻辑关联）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resourceId;

    /**
     * 资源类型：{@link #TYPE_GROUP} / {@link #TYPE_CHANNEL}
     */
    private String type;

    /**
     * 对上级暴露的 20 位国标码（仅 channel 有值）。
     * <p>本平台命名空间内唯一：通道原始 {@code channel_id} 只在设备内唯一，跨设备/级联透传可能撞码，
     * 撞码会让上级点播（INVITE 只带一个国标码）认错通道。故对上级暴露时统一改用此唯一码上报，
     * 上级 INVITE 亦按此码反查真实通道。同一通道对所有上级复用同一 exposed_code。</p>
     */
    private String exposedCode;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
