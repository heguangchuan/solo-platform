package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "国标设备状态统计")
@Data
public class GbDeviceStatusCountVO {

    @Schema(description = "状态（1在线/0离线/2未注册）")
    private Integer status;

    @Schema(description = "设备数")
    private Long count;
}
