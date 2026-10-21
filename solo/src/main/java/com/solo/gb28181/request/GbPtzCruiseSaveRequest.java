package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "新增巡航路线请求")
@Data
public class GbPtzCruiseSaveRequest {

    @Schema(description = "通道主键 ID")
    private Long channelDbId;

    @Schema(description = "巡航路线名称")
    private String name;

    @Schema(description = "巡航速度（1~255）")
    private Integer speed;

    @Schema(description = "每个预置位默认停留秒数")
    private Integer staySeconds;

    @Schema(description = "巡航点列表（可为空，后续再添加）")
    private List<Point> points;

    @Schema(description = "巡航点")
    @Data
    public static class Point {

        @Schema(description = "预置位编号")
        private Integer presetId;

        @Schema(description = "停留秒数（留空用路线默认值）")
        private Integer staySeconds;
    }
}
