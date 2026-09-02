package com.solo.onvif.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * ONVIF 设备分页查询请求
 *
 * <p>对应 {@code POST /api/onvif/device/page}。
 * 设备列表页直接绑定该 DTO；后端按所传条件过滤后返回 {@code { total, rows }} 形式的分页结果。</p>
 */
@Schema(description = "ONVIF 设备分页查询请求")
@Data
public class OnvifDevicePageRequest {

    @Schema(description = "页码（从 1 开始；默认 1）")
    private Long page = 1L;

    @Schema(description = "每页条数（默认 20）")
    private Long pageSize = 20L;

    @Schema(description = "显示名模糊匹配（可选）")
    private String name;

    @Schema(description = "IP 模糊匹配（可选）")
    private String host;

    @Schema(description = "在线状态精确过滤（可选）：0 离线，1 在线")
    private Integer status;
}
