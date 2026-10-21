package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "国标资源目录项分页查询请求")
@Data
public class GbDeviceChannelPageRequest {

    @Schema(description = "当前页码（从 1 开始）")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "所属设备国标 ID（精确匹配）")
    private String deviceId;

    @Schema(description = "20 位国标通道码（模糊匹配）")
    private String channelId;

    @Schema(description = "名称（模糊匹配）")
    private String name;

    @Schema(description = "在线状态：ON / OFF / UNREGISTERED")
    private String status;

    @Schema(description = "父分组 GB 码前缀（按前缀匹配）")
    private String parentId;

    @Schema(description = "资源类型过滤：0=仅通道、1=仅分组、null=全部")
    private Integer parental;

    @Schema(description = "数据来源：1 系统新增、2 表格导入、3 设备注册")
    private Integer source;

    @Schema(description = "云台类型：0 未知、1 球机、2 半球、3 固定枪机、4 遥控枪机（仅对通道生效）")
    private Integer ptzType;
}
