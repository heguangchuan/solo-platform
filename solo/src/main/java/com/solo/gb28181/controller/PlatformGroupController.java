package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.gb28181.request.GbPlatformGroupSaveRequest;
import com.solo.gb28181.response.GbPlatformGroupResponse;
import com.solo.gb28181.service.PlatformGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 上级平台推送目录分组（per-platform 推送树）CRUD 端点
 *
 * <p>本控制器只管分组树的 CRUD；挂载/卸载走 {@link GbParentPlatformController} 上的
 * {@code /share} 系列端点。分组与 share 表解耦。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code GET /{id}/share-group-tree} — 拉取该上级平台的推送目录分组树（按层级递归构树）</li>
 *   <li>{@code POST /{id}/share-group} — 新建该上级平台下的推送分组</li>
 *   <li>{@code PUT /{id}/share-group/{groupId}} — 改该上级平台下的推送分组</li>
 *   <li>{@code DELETE /{id}/share-group/{groupId}} — 删除该上级平台下的推送分组（拒绝有子节点）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/gb28181/parent-platform")
@RequiredArgsConstructor
public class PlatformGroupController {

    private final PlatformGroupService platformGroupService;

    /**
     * 拉取该上级平台的推送目录分组树（按层级递归构树）
     * @param id 上级平台主键 ID
     */
    @GetMapping("/{id}/share-group-tree")
    @RequirePermission({"gb28181:share-group:list"})
    public Result<List<GbPlatformGroupResponse>> shareGroupTree(@PathVariable("id") Long id) {
        return Result.success(platformGroupService.listGroupTree(id));
    }

    /**
     * 新建该上级平台下的推送分组
     * @param platformId 路径参数：上级平台主键 ID
     * @param req 分组资料：name 必填、code 必填（20 位国标编码格式校验）、parentId 必填（根填 0）
     */
    @PostMapping("/{id}/share-group")
    @RequirePermission({"gb28181:share-group:add"})
    public Result<Void> createShareGroup(
            @PathVariable("id") Long platformId,
            @Valid @RequestBody GbPlatformGroupSaveRequest req) {
        platformGroupService.createGroup(platformId, req);
        return Result.success();
    }

    /**
     * 改该上级平台下的推送分组
     * @param platformId 路径参数：上级平台主键 ID
     * @param groupId 路径参数：分组主键 ID
     * @param req 分组资料：name 必填、code 必填（20 位国标编码格式校验）、parentId 必填
     */
    @PutMapping("/{id}/share-group/{groupId}")
    @RequirePermission({"gb28181:share-group:edit"})
    public Result<Void> updateShareGroup(
            @PathVariable("id") Long platformId,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody GbPlatformGroupSaveRequest req) {
        platformGroupService.updateGroup(platformId, groupId, req);
        return Result.success();
    }

    /**
     * 删除该上级平台下的推送分组（拒绝有子节点）
     * <p>该分组下还有子分组时拒绝删除（需先清空子节点），返回 400。</p>
     *
     * @param platformId 路径参数：上级平台主键 ID
     * @param groupId 路径参数：分组主键 ID
     */
    @DeleteMapping("/{id}/share-group/{groupId}")
    @RequirePermission({"gb28181:share-group:delete"})
    public Result<Void> deleteShareGroup(
            @PathVariable("id") Long platformId,
            @PathVariable("groupId") Long groupId) {
        platformGroupService.deleteGroup(platformId, groupId);
        return Result.success();
    }
}
