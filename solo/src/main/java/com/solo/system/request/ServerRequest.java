package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "服务器新增/编辑请求")
@Data
public class ServerRequest {

    @Schema(description = "服务器名称")
    private String serverName;

    @Schema(description = "主机地址")
    private String host;

    @Schema(description = "SSH 端口")
    private Integer port;

    @Schema(description = "SSH 登录账号")
    private String username;

    @Schema(description = "认证方式（password/privateKey）")
    private String authType;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "私钥")
    private String privateKey;

    @Schema(description = "操作系统类型")
    private String osType;

    @Schema(description = "状态（0/1）")
    private Integer status;

    @Schema(description = "备注")
    private String description;
}
