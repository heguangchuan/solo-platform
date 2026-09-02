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
 * 国标接入禁用名单（黑名单）。
 *
 * <p><b>语义已从白名单翻转为黑名单</b>：设备与下级平台的注册时机不可控（谁什么时候来、报什么 ID
 * 都是对端决定），要求运维先登记才放行不现实。改为默认自由注册，只有在本表登记的国标 ID 会被
 * {@code RegisterRequestProcessor} 以 403 拒绝。</p>
 *
 * <p>接入后设备与下级平台不再区分——统一是一行 {@code gb_device}，运行态（在线/心跳/通道数）
 * 查那里。本表只承担"拒绝某个 ID"这一件事。</p>
 */
@Schema(description = "国标接入禁用名单（黑名单）")
@Data
@TableName("gb_platform")
public class GbPlatform {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 被禁用的国标 ID（20 位，设备或下级平台通用）
     */
    @Schema(description = "被禁用的国标 ID")
    private String platformId;

    /**
     * 备注名（可空；仅便于运维辨识，不参与业务判定）
     */
    @Schema(description = "备注名")
    private String name;

    /**
     * 禁用开关：0 禁用（拒绝注册，默认）1 临时放行（保留记录但不拦截）。
     * <p>留这个开关是为了"先放行观察、再决定是否彻底删掉这条记录"，不必删了又加。</p>
     */
    @Schema(description = "启用（0=禁用，1=临时放行）")
    private Integer enabled;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
