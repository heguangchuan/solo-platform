package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "录像段信息项")
@Data
public class RecordItemResponse {

    @Schema(description = "通道国标 ID")
    private String channelId;

    @Schema(description = "录像文件名（设备给的，未必有意义）")
    private String name;

    @Schema(description = "录像文件路径（设备本地路径，下载用）")
    private String filePath;

    @Schema(description = "录像地址")
    private String address;

    @Schema(description = "起始时间 ISO-8601（如 2026-06-30T08:00:00）")
    private String startTime;

    @Schema(description = "结束时间 ISO-8601")
    private String endTime;

    @Schema(description = "文件大小（字节），设备未提供时为 0")
    private Long fileSize;

    @Schema(description = "录像类型：time 计划 / alarm 报警 / manual 手动")
    private String type;

    @Schema(description = "录像设备 ID（NVR 等录像聚合设备，可为空）")
    private String recorderId;

    @Schema(description = "保密属性：0 不限、1 涉密")
    private Integer secrecy;
}
