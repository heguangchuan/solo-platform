package com.solo.gb28181.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 上级平台已共享的 catalog item 节点 id 集合
 *
 * <p>对应 {@code GET /api/gb28181/parent-platform/{id}/shared}。</p>
 * <p>回填 el-tree 勾选态用，元素为带前缀的节点 id（分组 {@code g_xxx} / 通道 {@code c_xxx}）。</p>
 */
@Schema(description = "上级平台已共享的 catalog item 节点 id 集合")
@Data
public class GbPlatformSharedResponse {

    @Schema(description = "已共享的 catalog item 节点 id：分组 g_xxx / 通道 c_xxx")
    private List<String> channelIds;
}
