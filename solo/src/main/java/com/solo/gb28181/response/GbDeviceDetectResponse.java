package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "批量检测设备存活结果")
@Data
public class GbDeviceDetectResponse {

    @Schema(description = "已发起探测的台数（结果异步更新，需刷新列表查看）")
    private int detecting;

    @Schema(description = "跳过的台数（无网络地址、从未注册，无法发包探测）")
    private int skipped;

    @Schema(description = "每台设备的处理明细")
    private List<Item> items = new ArrayList<>();

    @Schema(description = "设备检测处理明细项")
    @Data
    public static class Item {

        @Schema(description = "设备主键 ID（序列化为字符串避免 JS 大数精度丢失）")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        @Schema(description = "设备国标 ID（GB 码）")
        private String deviceId;

        @Schema(description = "处理结论：detecting（已发起探测）/ skipped（跳过）")
        private String result;

        public Item(Long id, String deviceId, String result) {
            this.id = id;
            this.deviceId = deviceId;
            this.result = result;
        }
    }
}
