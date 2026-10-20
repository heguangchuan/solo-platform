package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 国标设备实体
 * 对应数据库表 gb_device，存储下级 GB28181 设备的注册信息与状态
 */
@Schema(description = "国标设备")
@Data
@TableName("gb_device")
public class GbDevice implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 设备国标 ID（20 位），唯一
     */
    @Schema(description = "设备国标 ID（20 位）")
    private String deviceId;

    /**
     * 设备上报原始名称（DeviceInfo 响应中获取）
     */
    @Schema(description = "设备上报原始名称")
    private String reportName;

    /**
     * 用户自定义显示名称（展示优先级高于 reportName）
     */
    @Schema(description = "用户自定义显示名称")
    private String name;

    /**
     * 厂商
     */
    @Schema(description = "厂商")
    private String manufacturer;

    /**
     * 型号
     */
    @Schema(description = "型号")
    private String model;

    /**
     * 固件版本
     */
    @Schema(description = "固件版本")
    private String firmware;

    /**
     * 设备归属（DeviceInfo 响应回填）
     */
    @Schema(description = "设备归属")
    private String owner;

    /**
     * 行政区划编码（DeviceInfo 响应回填）
     */
    @Schema(description = "行政区划代码")
    private String civilCode;

    /**
     * 安装地址（DeviceInfo 响应回填）
     */
    @Schema(description = "安装地址")
    private String address;

    /**
     * 设备最后注册来源 IP
     */
    @Schema(description = "IP 地址")
    private String ip;

    /**
     * 收流IP覆盖；留空使用注册源IP，双栈内网互通时填设备内网IP
     */
    @Schema(description = "收流 IP 覆盖")
    private String recvIp;

    /**
     * 设备最后注册来源端口
     */
    @Schema(description = "端口")
    private Integer port;

    /**
     * 本平台用于和该设备通信的本机 IP（多网卡时必须记录）
     */
    @Schema(description = "本机 IP")
    private String localIp;

    /**
     * 设备主机地址 ip:port（冗余存储，便于查询）
     */
    @Schema(description = "设备主机地址")
    private String hostAddress;

    /**
     * 传输协议 UDP/TCP
     */
    @Schema(description = "传输模式（UDP/TCP）")
    private String transport;

    /**
     * 注册有效期（秒）
     */
    @Schema(description = "注册有效期（秒）")
    private Integer expires;

    /**
     * 字符集（GB2312/UTF-8）
     */
    @Schema(description = "字符集（GB2312/UTF-8）")
    private String charset;

    /**
     * 在线状态：0 离线，1 在线，2 未注册（系统新增/导入的记录默认态，仅由 SIP 交互改变）
     */
    @Schema(description = "在线状态（0=离线，1=在线，2=未注册）")
    private Integer status;

    /**
     * 数据来源：1 系统新增，2 表格导入，3 设备注册（SIP 自动入库）
     */
    @Schema(description = "数据来源（1=系统新增/2=表格导入/3=设备注册）")
    private Integer source;

    /**
     * 最后注册时间
     */
    @Schema(description = "最近注册时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerTime;

    /**
     * 最后心跳时间
     */
    @Schema(description = "最近心跳")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime keepaliveTime;

    /**
     * 最后 Catalog 同步完成时间
     */
    @Schema(description = "最后Catalog同步时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCatalogSyncTime;

    /**
     * 目录订阅周期（秒），0 表示不订阅
     * <p>开启后平台主动向设备发送 SUBSCRIBE（Event: catalog），
     * 设备通道变更时通过 NOTIFY 主动推送，无需周期性全量查询。</p>
     */
    @Schema(description = "目录订阅周期（秒），0=不订阅")
    private Integer subscribeCycleForCatalog;

    @Schema(description = "创建时间")
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
