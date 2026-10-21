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

@Schema(description = "国标设备通道")
@Data
@TableName("gb_device_channel")
public class GbDeviceChannel {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "设备国标 ID")
    private String deviceId;

    @Schema(description = "通道国标 ID（20 位）")
    private String channelId;

    @Schema(description = "通道上报原始名称")
    private String reportName;

    @Schema(description = "自定义名称")
    private String name;

    @Schema(description = "厂商")
    private String manufacturer;

    @Schema(description = "型号")
    private String model;

    @Schema(description = "设备归属")
    private String owner;

    @Schema(description = "行政区划代码")
    private String civilCode;

    @Schema(description = "警区")
    private String block;

    @Schema(description = "安装地址")
    private String address;

    @Schema(description = "资源类型（0=通道，1=分组，无 NULL 语义）")
    private Integer parental;

    @Schema(description = "安全接入方式")
    private Integer safetyWay;

    @Schema(description = "注册方式")
    private Integer registerWay;

    @Schema(description = "证书编号")
    private String certNum;

    @Schema(description = "证书认证标识")
    private Integer certifiable;

    @Schema(description = "错误码")
    private Integer errCode;

    @Schema(description = "证书有效期止")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "保密等级")
    private Integer secrecy;

    @Schema(description = "IP 地址")
    private String ipAddress;

    @Schema(description = "端口")
    private Integer port;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "在线状态（ON/OFF/UNREGISTERED，仅由 SIP 交互改变，仅通道）")
    private String status;

    @Schema(description = "经度（原样存取，不做坐标系转换；页面手填或设备/下级平台 Catalog 上报是什么就存什么）")
    private Double longitude;
    @Schema(description = "纬度，约定同经度：原样存取，不做坐标系转换")
    private Double latitude;

    @Schema(description = "云台类型：0 未知、1 球机、2 半球、3 固定枪机、4 遥控枪机（仅通道）")
    private Integer ptzType;

    @Schema(description = "父节点 ID")
    private String parentId;

    @Schema(description = "数据来源（1=系统新增/2=表格导入/3=设备注册，Catalog 同步自动入库）")
    private Integer source;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "对上级暴露的国标码")
    @TableField(exist = false)
    private String exposedCode;

    public String getExposedOrChannelId() {
        return (exposedCode != null && !exposedCode.isBlank()) ? exposedCode : channelId;
    }
}
