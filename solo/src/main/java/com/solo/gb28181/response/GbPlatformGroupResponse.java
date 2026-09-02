package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbPlatformGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 推送目录分组树响应项
 *
 * <p>对应 {@code GET /api/gb28181/parent-platform/{id}/share-group-tree}。</p>
 *
 * <p>字段与分组实体完全一致，仅外加 {@link #children} 字段。
 * 前端用此结构渲染树形控件，{@code children} 为空数组的节点是叶子节点。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GbPlatformGroupResponse extends GbPlatformGroup {

    /**
     * 子节点（树形结构）
     */
    private List<GbPlatformGroupResponse> children = new ArrayList<>();
}
