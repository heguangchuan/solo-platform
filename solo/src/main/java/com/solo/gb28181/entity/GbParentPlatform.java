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
 * 上级平台配置
 * <p>本平台作为下级向上级平台注册时的配置。一行 = 一个上级平台。
 * 注册成功后 status 回填为 1（在线），注册失败/超时回填 0。</p>
 */
@Data
@TableName("gb_parent_platform")
public class GbParentPlatform {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 上级平台名称
     */
    private String name;

    /**
     * 上级平台国标编码（SIP 服务器 ID，20 位）
     */
    private String serverGbId;

    /**
     * 上级平台国标域
     */
    private String serverGbDomain;

    /**
     * 上级平台 SIP IP
     */
    private String serverIp;

    /**
     * 上级平台 SIP 端口
     */
    private Integer serverPort;

    /**
     * 本平台作为下级的国标编号（向上注册用）
     */
    private String deviceGbId;

    /**
     * 本平台 SIP IP（留空取 gb28181.ip）
     */
    private String deviceIp;

    /**
     * 本平台 SIP 端口（留空取 gb28181.port）
     */
    private Integer devicePort;

    /**
     * SIP 认证用户名（默认同 device_gb_id）
     */
    private String username;

    /**
     * SIP 认证密码
     */
    private String password;

    /**
     * 注册有效期（秒）
     */
    private Integer expires;

    /**
     * 心跳周期（秒）
     */
    private Integer keepTimeout;

    /**
     * 传输协议 UDP/TCP
     */
    private String transport;

    /**
     * 字符集
     */
    private String characterSet;

    /**
     * 启用状态：0 禁用 1 启用
     */
    private Integer enabled;

    /**
     * 在线状态：0 离线 1 在线（后续注册成功回填）
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
