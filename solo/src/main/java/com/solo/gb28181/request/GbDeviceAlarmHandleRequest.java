package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备报警处置请求
 *
 * <p>对应 {@code POST /api/gb28181/alarm/handle}。</p>
 */
@Schema(description = "设备报警处置请求")
@Data
public class GbDeviceAlarmHandleRequest {

    @Schema(description = "报警主键 ID")
    private Long id;

    @Schema(description = "处置状态：1 已确认、2 已处置、3 误报")
    private Integer handleStatus;

    @Schema(description = "处置备注")
    private String handleRemark;
}
