package com.solo.gb28181.response;

import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量检测设备存活结果（fire-and-forget）
 * <p>探测采用异步回调更新状态：本响应只汇报「已发起多少台探测、跳过多少台」，
 * 真实在线/离线结论由 SIP 应答/超时回调异步写回 {@code gb_device.status}，
 * 前端提示后自行刷新列表查看最新状态。</p>
 */
@Data
public class GbDeviceDetectResponse {

    /** 已发起探测台数（结果异步更新，需刷新列表查看） */
    private int detecting;

    /** 跳过台数（无网络地址、从未注册，无法发包探测） */
    private int skipped;

    /** 每台设备的处理明细 */
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String deviceId;
        /** 处理结论：detecting（已发起探测）/ skipped（跳过） */
        private String result;

        public Item(Long id, String deviceId, String result) {
            this.id = id;
            this.deviceId = deviceId;
            this.result = result;
        }
    }
}
