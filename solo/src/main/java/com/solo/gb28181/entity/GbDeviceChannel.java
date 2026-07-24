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
 * 国标设备通道
 * <p>对应 GB/T 28181-2016 Catalog 报文中的每一个 Item。</p>
 * <p>逻辑关联 {@link GbDevice}：通过 {@code device_db_id}（主键ID）和 {@code device_id}（国标ID冗余）双向定位，
 * 不在 DB 层建外键。</p>
 */
@Data
@TableName("gb_device_channel")
public class GbDeviceChannel {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属设备数据库主键（逻辑关联 gb_device.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deviceDbId;

    /** 所属设备国标ID（冗余存储，便于跨表查询不 join） */
    private String deviceId;

    /** 通道国标ID（20位） */
    private String channelId;

    /** 通道上报原始名称（Catalog 目录同步中获取） */
    private String reportName;

    /**
     * 用户自定义显示名称（展示优先级高于 reportName）
     */
    private String name;

    private String manufacturer;
    private String model;

    /** 设备归属 */
    private String owner;

    /** 行政区划编码 */
    private String civilCode;

    /** 警区 */
    private String block;

    /** 安装地址 */
    private String address;

    /** 是否有子设备：0 否 1 是 */
    private Integer parental;

    /** 父节点ID（资源目录用） */
    private String parentId;

    /** 信令安全模式 */
    private Integer safetyWay;

    /** 注册方式 */
    private Integer registerWay;

    /** 证书序列号 */
    private String certNum;

    /** 证书有效标识 */
    private Integer certifiable;

    /** 证书无效原因码 */
    private Integer errCode;

    /** 证书终止有效期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 保密属性 */
    private Integer secrecy;

    private String ipAddress;
    private Integer port;
    private String password;

    /** 在线状态 ON / OFF / UNREGISTERED（系统新增/导入的记录默认态，仅由 SIP 交互改变） */
    private String status;

    private Double longitude;
    private Double latitude;

    /** 云台类型：0 未知 1 球机 2 半球 3 固定枪机 4 遥控枪机 */
    private Integer ptzType;

    /** 运行时点播 stream id（点播开始时回填，结束清空） */
    private String streamId;

    /**
     * 所属平台 ID：{@code 0000} = 自有平台（默认）；其他值代表上级级联平台 ID。
     * 应与所属 {@link GbDevice#platformId} 保持一致，由 Catalog 同步流程写入。
     */
    private String platformId;

    /**
     * 所属资源目录分组（逻辑关联 {@link GbGroup#getGroupId()}，20 位 215 业务分组码）。
     * <p>通道注册/导入/新增未指定时，由入库流程回填 {@code gb28181.default-group.id}；
     * 编辑不动此字段，仅由目录编排功能变更。</p>
     */
    private String groupId;

    /**
     * 数据来源：1 系统新增，2 表格导入，3 设备注册（Catalog 同步自动入库）
     */
    private Integer source;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
