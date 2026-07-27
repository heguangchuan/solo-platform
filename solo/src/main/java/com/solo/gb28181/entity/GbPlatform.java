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
 * 下级级联平台白名单
 * <p>SIP 接入时视为设备（gb_device），平台与设备通过 platform_id 自动关联
 * （{@code gb_platform.platform_id == gb_device.device_id}）。
 * 运行态（在线/心跳/通道数）查关联的 gb_device 行，不在本表冗余。</p>
 */
@Data
@TableName("gb_platform")
public class GbPlatform {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 下级平台国标 ID（20 位），唯一且等于 gb_device.device_id
     */
    private String platformId;

    /**
     * 平台名称
     */
    private String name;

    /**
     * 注册鉴权专属密钥（后端自动生成 32 位 UUID，不可编辑）
     */
    private String authKey;

    /**
     * 启用状态：0 禁用（拒绝注册）1 启用
     */
    private Integer enabled;

    /**
     * 备注
     */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}