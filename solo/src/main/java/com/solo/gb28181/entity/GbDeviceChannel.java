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
 * 国标资源目录项（通道 + 分组合一，ADR-023）。
 *
 * <p>对应 GB/T 28181-2016 Catalog 报文中的每一个 {@code <Item>}。靠 {@link #parental} 区分：</p>
 * <ul>
 *   <li>{@code parental=0}：通道（叶子），有 manufacturer/model/ptzType/status 等业务字段</li>
 *   <li>{@code parental=1}：分组（容器），仅 id/channel_id/name/parent_id/sort 有语义，其他字段为 NULL</li>
 * </ul>
 *
 * <p>{@link #parentId} 存的是父 catalog item 的 <b>20 位国标码</b>（与父行 {@code channel_id} 对齐），
 * 字符串类型，{@code "0"} 表根（无父）。GB 协议 {@code <ParentID>} 直接 round-trip，无须做 GB 码 ↔
 * 雪花 id 的转换。{@link #deviceId} 仅通道有值（GB 码字符串），分组为 NULL。</p>
 *
 * <p>逻辑关联 {@link GbDevice}：通过 {@code device_id}（国标ID 字符串）双向定位，
 * 不在 DB 层建外键；去掉 {@code device_db_id} 雪花主键引用，关系表只有一份 GB 码字段。</p>
 */
@Data
@TableName("gb_device_channel")
public class GbDeviceChannel {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属设备国标ID（逻辑关联 gb_device.device_id）；分组（parental=1）时为 NULL */
    private String deviceId;

    /**
     * catalog item 20 位国标码：
     * <ul>
     *   <li>分组：第 11-13 位 215 业务分组 / 216 虚拟组织；channel_id 全局唯一由应用层检查（无 DB 唯一键）</li>
     *   <li>通道：第 11-13 位 130-198（默认 131 摄像机 / 132 IPC）；设备内唯一（uk_device_channel）</li>
     * </ul>
     */
    private String channelId;

    /** catalog item 上报原始名称（Catalog 目录同步；分组时为 NULL） */
    private String reportName;

    /** 用户自定义显示名称（展示优先级高于 reportName） */
    private String name;

    private String manufacturer;
    private String model;

    private String owner;

    private String civilCode;

    private String block;

    private String address;

    /** 0=通道（叶子）/ 1=分组（容器）。无 NULL 语义。 */
    private Integer parental;

    private Integer safetyWay;

    private Integer registerWay;

    private String certNum;

    private Integer certifiable;

    private Integer errCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer secrecy;

    private String ipAddress;
    private Integer port;
    private String password;

    /** 在线状态 ON/OFF/UNREGISTERED（仅由 SIP 交互改变；仅通道） */
    private String status;

    private Double longitude;
    private Double latitude;

    /** 云台类型：0 未知 1 球机 2 半球 3 固定枪机 4 遥控枪机（仅通道） */
    private Integer ptzType;

    /**
     * 父 catalog item 的 20 位国标码（指向同表 channel_id）；"0"=根（无父）。
     * <p>入库流程（Catalog 同步 / 手动新增）未指定父项时统一回填 "0"；编辑可改，级联回上级
     * 时按 channel_id 反查父行生成 XML ParentID。详见 ADR-023。</p>
     */
    private String parentId;

    /** 数据来源：1 系统新增，2 表格导入，3 设备注册（Catalog 同步自动入库） */
    private Integer source;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 对上级暴露的国标码（瞬态，不入本表）。仅当按某上级查共享通道时，由 JOIN
     * {@code gb_platform_share.exposed_code} 带出，用于级联上报 Catalog / 上级点播反查。
     * 普通通道查询此字段为 null。
     */
    @TableField(exist = false)
    private String exposedCode;
}
