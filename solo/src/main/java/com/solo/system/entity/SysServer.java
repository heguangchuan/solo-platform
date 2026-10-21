package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(description = "服务器")
@Data
@TableName("sys_server")
public class SysServer {

    @Schema(description = "服务器 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "服务器名称")
    private String serverName;

    @Schema(description = "主机地址")
    private String host;

    @Schema(description = "SSH 端口")
    private Integer port;

    @Schema(description = "登录用户名")
    private String username;

    @Schema(description = "认证方式（PASSWORD=密码，PRIVATE_KEY=密钥）")
    private String authType;

    @Schema(description = "密码密文（仅入参）")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordSecret;

    @Schema(description = "私钥密文（仅入参）")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String privateKeySecret;

    @Schema(description = "操作系统类型")
    private String osType;

    @Schema(description = "状态（0=停用，1=启用）")
    private Integer status;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "逻辑删除（0=未删，1=已删）")
    @TableLogic
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime updateTime;
}
