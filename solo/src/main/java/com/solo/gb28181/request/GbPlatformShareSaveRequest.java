package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 上级平台共享资源保存请求
 * <p>对应 {@code POST /api/gb28181/parent-platform/{id}/shared}。</p>
 * <p>全量覆盖：{@code channelIds} 为前端 el-tree 勾选（含半选父节点）的带前缀节点 id，
 * 同一份契约同时承载分组（{@code g_} 前缀）和通道（{@code c_} 前缀）——按节点类型前缀分类入库。</p>
 */
@Schema(description = "上级平台共享资源保存请求")
@Data
public class GbPlatformShareSaveRequest {

    @Schema(description = "勾选的 catalog item 节点 id：分组 g_xxx / 通道 c_xxx")
    private List<String> channelIds;
}
