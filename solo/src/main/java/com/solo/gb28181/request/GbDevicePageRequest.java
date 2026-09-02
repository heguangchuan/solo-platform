package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 国标设备分页查询请求
 *
 * <p>对应 {@code POST /api/gb28181/device/page}。分页参数：{@code page} 从 1 开始，默认 1；{@code pageSize} 默认 20。
 * 响应结构为 {@code { total, rows }}。</p>
 */
@Schema(description = "国标设备分页查询请求")
@Data
public class GbDevicePageRequest {

    @Schema(description = "当前页码（从 1 开始）")
    private Long page = 1L;

    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    @Schema(description = "设备国标 ID（模糊匹配）")
    private String deviceId;

    @Schema(description = "设备名称（模糊匹配）")
    private String name;

    @Schema(description = "在线状态：0 离线、1 在线、2 未注册")
    private Integer status;

    @Schema(description = "数据来源：1 系统新增、2 表格导入、3 设备注册")
    private Integer source;
}
