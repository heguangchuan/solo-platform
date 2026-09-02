package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 服务器实体类
 * 对应数据库表 sys_server，存储服务器基础信息
 */
@Schema(description = "服务器")
@Data
@TableName("sys_server")
public class SysServer {

    /**
     * 服务器ID，主键
     */
    @Schema(description = "服务器 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 服务器名称
     */
    @Schema(description = "服务器名称")
    private String serverName;

    /**
     * 主机地址，IP或域名
     */
    @Schema(description = "主机地址")
    private String host;

    /**
     * SSH端口
     */
    @Schema(description = "SSH 端口")
    private Integer port;

    /**
     * 登录用户名
     */
    @Schema(description = "登录用户名")
    private String username;

    /**
     * 认证方式：PASSWORD密码 PRIVATE_KEY密钥
     */
    @Schema(description = "认证方式（PASSWORD=密码，PRIVATE_KEY=密钥）")
    private String authType;

    /**
     * 密码密文，仅写入，不返回前端
     */
    @Schema(description = "密码密文（仅入参）")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordSecret;

    /**
     * 私钥密文，仅写入，不返回前端
     */
    @Schema(description = "私钥密文（仅入参）")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String privateKeySecret;

    /**
     * 操作系统类型
     */
    @Schema(description = "操作系统类型")
    private String osType;

    /**
     * 状态：0-停用，1-启用
     */
    @Schema(description = "状态（0=停用，1=启用）")
    private Integer status;

    /**
     * 描述
     */
    @Schema(description = "描述")
    private String description;

    /**
     * 逻辑删除标识：0-未删除，1-已删除
     */
    @Schema(description = "逻辑删除（0=未删，1=已删）")
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间，插入时自动填充
     */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间，插入和修改时自动填充
     */
    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime updateTime;
}
