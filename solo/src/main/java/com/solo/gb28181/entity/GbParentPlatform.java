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

@Schema(description = "上级平台配置")
@Data
@TableName("gb_parent_platform")
public class GbParentPlatform {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "上级平台名称")
    private String name;

    @Schema(description = "上级 SIP 国标 ID（20 位）")
    private String serverGbId;

    @Schema(description = "上级 SIP 域")
    private String serverGbDomain;

    @Schema(description = "上级平台 IP")
    private String serverIp;

    @Schema(description = "上级平台端口")
    private Integer serverPort;

    @Schema(description = "SIP 认证账号（默认同 gb28181.id）")
    private String username;

    @Schema(description = "SIP 认证密码")
    private String password;

    @Schema(description = "注册有效期（秒）")
    private Integer expires;

    @Schema(description = "心跳超时（秒）")
    private Integer keepTimeout;

    @Schema(description = "传输模式（UDP/TCP）")
    private String transport;

    @Schema(description = "字符集（GB2312/UTF-8）")
    private String characterSet;

    @Schema(description = "启用（0=禁用，1=启用）")
    private Integer enabled;

    @Schema(description = "在线状态（0=离线，1=在线，注册成功回填）")
    private Integer status;

    @Schema(description = "目录响应单包 Item 数（1/2/4/8；NULL 走 gb28181.cascade.catalog-page-size 全局默认，某些品牌上级 > 8 会拒收/拆包失败，需单独配小档位）")
    private Integer catalogGroup;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
