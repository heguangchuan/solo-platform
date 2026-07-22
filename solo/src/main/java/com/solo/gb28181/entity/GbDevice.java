package com.solo.gb28181.entity;

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
 * 国标设备实体
 * 对应数据库表 gb_device，存储下级 GB28181 设备的注册信息与状态
 */
@Data
@TableName("gb_device")
public class GbDevice {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 设备国标 ID（20 位），唯一
     */
    private String deviceId;

    /**
     * 设备上报原始名称（DeviceInfo 响应中获取）
     */
    private String reportName;

    /**
     * 用户自定义显示名称（展示优先级高于 reportName）
     */
    private String name;

    /**
     * 厂商
     */
    private String manufacturer;

    /**
     * 型号
     */
    private String model;

    /**
     * 固件版本
     */
    private String firmware;

    /**
     * 设备 SIP 域
     */
    private String sipDomain;

    /**
     * 设备最后注册来源 IP
     */
    private String ip;

    /**
     * 设备最后注册来源端口
     */
    private Integer port;

    /**
     * 本平台用于和该设备通信的本机 IP（多网卡时必须记录）
     */
    private String localIp;

    /**
     * 设备主机地址 ip:port（冗余存储，便于查询）
     */
    private String hostAddress;

    /**
     * 传输协议 UDP/TCP
     */
    private String transport;

    /**
     * 注册有效期（秒）
     */
    private Integer expires;

    /**
     * 字符集（GB2312/UTF-8）
     */
    private String charset;

    /**
     * 在线状态：0 离线，1 在线，2 未注册（系统新增/导入的记录默认态，仅由 SIP 交互改变）
     */
    private Integer status;

    /**
     * 数据来源：1 系统新增，2 表格导入，3 设备注册（SIP 自动入库）
     */
    private Integer source;

    /**
     * 通道总数
     */
    private Integer channelCount;

    /**
     * 最后注册时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerTime;

    /**
     * 最后心跳时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime keepaliveTime;

    /**
     * 最后 Catalog 同步完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCatalogSyncTime;

    /**
     * 所属平台 ID：{@code 0000} = 自有平台（默认）；其他值代表上级级联平台 ID，
     * 后续 S6 级联实装后由级联接入流程写入，用以区分设备来源。
     */
    private String platformId;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
