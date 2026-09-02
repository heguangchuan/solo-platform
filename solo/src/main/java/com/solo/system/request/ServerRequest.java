package com.solo.system.request;

import lombok.Data;

/**
 * 服务器新增/编辑请求
 *
 * <p>对应接口 {@code POST /api/server}、{@code PUT /api/server/{id}}。
 * 凭据（密码 / 私钥）落库加密，编辑后原凭据会被覆盖——不修改凭据字段也能正常保存。</p>
 */
@Data
public class ServerRequest {

    /** 服务器名称（必填，管理用） */
    private String serverName;

    /** 主机地址（必填，IP 或域名） */
    private String host;

    /** SSH 端口（必填，默认 22） */
    private Integer port;

    /** SSH 登录账号（必填） */
    private String username;

    /** 认证方式（必填）：{@code password} / {@code privateKey} */
    private String authType;

    /** 密码（{@code authType=password} 时必填） */
    private String password;

    /** 私钥（PEM 原文，{@code authType=privateKey} 时必填） */
    private String privateKey;

    /** 操作系统类型（可选）：{@code linux} / {@code windows} */
    private String osType;

    /** 状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    private Integer status;

    /** 备注（可选） */
    private String description;
}
