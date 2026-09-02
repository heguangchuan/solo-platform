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
 * 上级平台配置
 * <p>本平台作为下级向上级平台注册时的配置。一行 = 一个上级平台。
 * 注册成功后 status 回填为 1（在线），注册失败/超时回填 0。</p>
 */
@Schema(description = "上级平台配置")
@Data
@TableName("gb_parent_platform")
public class GbParentPlatform {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 上级平台名称
     */
    @Schema(description = "上级平台名称")
    private String name;

    /**
     * 上级平台国标编码（SIP 服务器 ID，20 位）
     */
    @Schema(description = "上级 SIP 国标 ID")
    private String serverGbId;

    /**
     * 上级平台国标域
     */
    @Schema(description = "上级 SIP 域")
    private String serverGbDomain;

    /**
     * 上级平台 SIP IP
     */
    @Schema(description = "上级平台 IP")
    private String serverIp;

    /**
     * 上级平台 SIP 端口
     */
    @Schema(description = "上级平台端口")
    private Integer serverPort;

    /**
     * SIP 认证用户名（默认同 gb28181.id）
     */
    @Schema(description = "SIP 认证账号")
    private String username;

    /**
     * SIP 认证密码
     */
    @Schema(description = "SIP 认证密码")
    private String password;

    /**
     * 注册有效期（秒）
     */
    @Schema(description = "注册有效期（秒）")
    private Integer expires;

    /**
     * 心跳周期（秒）
     */
    @Schema(description = "心跳超时（秒）")
    private Integer keepTimeout;

    /**
     * 传输协议 UDP/TCP
     */
    @Schema(description = "传输模式（UDP/TCP）")
    private String transport;

    /**
     * 字符集
     */
    @Schema(description = "字符集（GB2312/UTF-8）")
    private String characterSet;

    /**
     * 启用状态：0 禁用 1 启用
     */
    @Schema(description = "启用（0=禁用，1=启用）")
    private Integer enabled;

    /**
     * 在线状态：0 离线 1 在线（后续注册成功回填）
     */
    @Schema(description = "在线状态（0=离线，1=在线）")
    private Integer status;

    /**
     * Catalog 响应单包 Item 数（ADR-027）：1 / 2 / 4 / 8。
     * <p>NULL 表示走 {@code gb28181.cascade.catalog-page-size} 全局默认。某些品牌上级 > 8 会拒收 / 拆包失败，
     * 对这些上级需要单独配小档位。</p>
     */
    @Schema(description = "目录响应单包 Item 数（1/2/4/8）")
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
