package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbPlatformGroupSaveRequest;
import com.solo.gb28181.request.UnmountedChannelPageRequest;
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
 * 上级平台推送目录分组（per-platform 推送树）
 *
 * <p>提供查询 + CRUD。通道挂载 / 卸载待后续迭代"推送保存"功能。</p>
 */
@RestController
@RequestMapping("/api/gb28181/parent-platform")
@RequiredArgsConstructor
public class PlatformGroupController {

    private final PlatformGroupService platformGroupService;

    /**
     * 拉取该上级平台的推送目录分组树（按 parent_id 递归构树）
     */
    @GetMapping("/{id}/share-group-tree")
    @RequirePermission({"gb28181:share-group:list"})
    public Result<List<GbPlatformGroupResponse>> shareGroupTree(@PathVariable("id") Long id) {
        return Result.success(platformGroupService.listGroupTree(id));
    }

    /**
     * 分页拉取该上级平台下未挂载的通道
     */
    @PostMapping("/{id}/unmounted-channels/page")
    @RequirePermission({"gb28181:share-group:list"})
    public Result<PageResult<GbDeviceChannel>> unmountedChannelsPage(
            @PathVariable("id") Long id,
            @RequestBody UnmountedChannelPageRequest req) {
        IPage<GbDeviceChannel> page = platformGroupService.pageUnmountedChannels(id, req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新建该上级平台下的推送分组
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
