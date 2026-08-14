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
 * 上级平台共享通道（推送给上级的通道，ADR-023）。
 * <p>本平台作为下级向上级共享资源时，选中的通道记录。
 * 一行 = 一个上级平台 × 一个推送分组 × 一个通道，无物理外键（ADR-011）。</p>
 *
 * <p><b>只存通道</b>：{@link #resourceId} 指向 {@code gb_device_channel} 中 {@code parental=0} 的行。
 * 分组（{@code parental=1}）是本平台内部的目录组织，当前不推送给上级（Catalog XML 只发通道 Item）；
 * 若未来要推分组 Item，届时再加类型区分字段。</p>
 *
 * <p>推送国标码（{@code <DeviceID>}）：{@code exposedCode ?? channelId}，由消费方
 * （{@code CascadeSendService} / {@code PlatformCatalogXml}）通过
 * {@link GbDeviceChannel#getExposedOrChannelId()} 取。</p>
 *
 * <p>与 {@code gb_platform_group}（推送分组树）关联：UI 强制先在弹窗左侧选中分组才能挂载。
 * 同一通道在同一平台下只能挂载一次（{@code uk_platform_resource}），即只能落在某一个分组下；
 * 想换组要先卸载旧组的 share。</p>
 */
@Data
@TableName("gb_platform_share")
public class GbPlatformShare {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 上级平台主键（逻辑关联 gb_parent_platform.id）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long platformId;

    /**
     * 所属推送分组主键（逻辑关联 gb_platform_group.id）。
     * <p>UI 强制先在弹窗左侧选中分组才能挂载；同一通道在本平台只能落在一个分组下。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    /**
     * 通道主键（逻辑关联 gb_device_channel.id，{@code parental=0} 的行）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resourceId;

    /**
     * 对上级暴露的 20 位国标码（撞码时分配；不撞码时为 NULL 表示透传 {@code channel_id}）。
     * <p>本平台命名空间内唯一：通道原始 {@code channel_id} 只在设备内唯一，跨设备/级联透传可能撞码，
     * 撞码会让上级点播（INVITE 只带一个国标码）认错通道。故对上级暴露时统一改用此唯一码上报，
     * 上级 INVITE 亦按此码反查真实通道。同一通道对所有上级复用同一 exposed_code。</p>
     * <p><b>撞码判定是平台级的</b>（不是分组级）——上级看到的是扁平 DeviceID 空间，跨组同码也会
     * 让上级认错通道。</p>
     */
    private String exposedCode;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
