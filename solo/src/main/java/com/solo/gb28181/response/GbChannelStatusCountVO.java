package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "国标通道状态统计")
@Data
public class GbChannelStatusCountVO {

    @Schema(description = "资源类型（0通道/1分组）")
    private Integer parental;

    @Schema(description = "状态（通道 ON/OFF/UNREGISTERED；分组固定 ON）")
    private String status;

    @Schema(description = "数量")
    private Long count;
}
