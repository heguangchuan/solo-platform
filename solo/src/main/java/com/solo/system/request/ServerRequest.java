package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 服务器新增/编辑请求
 *
 * <p>对应接口 {@code POST /api/server}、{@code PUT /api/server/{id}}。
 * 凭据（密码 / 私钥）落库加密，编辑后原凭据会被覆盖——不修改凭据字段也能正常保存。</p>
 */
@Schema(description = "服务器新增/编辑请求")
@Data
public class ServerRequest {

    /** 服务器名称（必填，管理用） */
    @Schema(description = "服务器名称")
    private String serverName;

    /** 主机地址（必填，IP 或域名） */
    @Schema(description = "主机地址")
    private String host;

    /** SSH 端口（必填，默认 22） */
    @Schema(description = "SSH 端口")
    private Integer port;

    /** SSH 登录账号（必填） */
    @Schema(description = "SSH 登录账号")
    private String username;

    /** 认证方式（必填）：{@code password} / {@code privateKey} */
    @Schema(description = "认证方式（password/privateKey）")
    private String authType;

    /** 密码（{@code authType=password} 时必填） */
    @Schema(description = "密码")
    private String password;

    /** 私钥（PEM 原文，{@code authType=privateKey} 时必填） */
    @Schema(description = "私钥")
    private String privateKey;

    /** 操作系统类型（可选）：{@code linux} / {@code windows} */
    @Schema(description = "操作系统类型")
    private String osType;

    /** 状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;

    /** 备注（可选） */
    @Schema(description = "备注")
    private String description;
}
