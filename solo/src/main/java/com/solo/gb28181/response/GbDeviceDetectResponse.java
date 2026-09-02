package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量检测设备存活结果（fire-and-forget）
 *
 * <p>对应 {@code POST /api/gb28181/device/detect} 返回体。</p>
 * <p>探测采用异步更新：本响应只汇报「已发起多少台探测、跳过多少台」，
 * 真实在线/离线结论由 SIP 应答/超时回调异步写回设备在线状态字段，
 * 前端提示后自行刷新列表查看最新状态。</p>
 */
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
