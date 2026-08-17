package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.request.GbParentPlatformBatchDeleteRequest;
import com.solo.gb28181.request.GbParentPlatformPageRequest;
import com.solo.gb28181.request.GbPlatformShareSaveRequest;
import com.solo.gb28181.request.UnmountedChannelPageRequest;
import com.solo.gb28181.response.GbLocalPlatformInfo;
import com.solo.gb28181.response.GbPlatformSharedResponse;
import com.solo.gb28181.response.GbShareTreeNodeResponse;
import com.solo.gb28181.service.CatalogShareService;
import com.solo.gb28181.service.GbParentPlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 上级平台管理
 * 本平台作为下级向上级平台注册的配置 CRUD
 */
@RestController
@RequestMapping("/api/gb28181/parent-platform")
@RequiredArgsConstructor
public class GbParentPlatformController {

    private final GbParentPlatformService gbParentPlatformService;
    private final CatalogShareService catalogShareService;
    private final Gb28181Properties gb28181Properties;

    /**
     * 分页查询上级平台配置
     */
    @PostMapping("/page")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<PageResult<GbParentPlatform>> page(@RequestBody GbParentPlatformPageRequest request) {
        Page<GbParentPlatform> page = gbParentPlatformService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增上级平台配置
     */
    @PostMapping("/save")
    @RequirePermission({"gb28181:parent-platform:add"})
    public Result<Void> save(@RequestBody GbParentPlatform platform) {
        gbParentPlatformService.create(platform);
        return Result.success();
    }

    /**
     * 编辑上级平台配置
     */
    @PostMapping("/update")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Void> update(@RequestBody GbParentPlatform platform) {
        if (platform.getId() == null) {
            throw new ServiceException("上级平台ID不能为空");
        }
        gbParentPlatformService.updatePlatform(platform);
        return Result.success();
    }

    /**
     * 删除上级平台配置
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"gb28181:parent-platform:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        GbParentPlatform platform = gbParentPlatformService.getById(id);
        if (platform != null) {
            gbParentPlatformService.removeById(id);
        }
        return Result.success();
    }

    /**
     * 批量删除上级平台配置
     */
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:parent-platform:delete"})
    public Result<Void> batchDelete(@RequestBody GbParentPlatformBatchDeleteRequest request) {
        gbParentPlatformService.deleteBatch(request.getIds());
        return Result.success();
    }

    /**
     * 获取本平台国标身份（id / 域 / IP / 端口 / 鉴权密码等）
     * <p>上级平台配置白名单时需要这些信息。前端管理面"上级平台"页面顶部展示。</p>
     */
    @GetMapping("/local-info")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<GbLocalPlatformInfo> localInfo() {
        GbLocalPlatformInfo info = new GbLocalPlatformInfo();
        info.setId(gb28181Properties.getId());
        info.setDomain(gb28181Properties.getDomain());
        info.setIp(gb28181Properties.getIp());
        info.setPort(gb28181Properties.getPort());
        info.setPassword(gb28181Properties.getPassword());
        info.setRegisterExpires(gb28181Properties.getRegisterExpires());
        return Result.success(info);
    }

    // ==================== 通道推送 ====================

    /**
     * 获取可共享的资源目录树（分组 + 通道）
     */
    @GetMapping("/share-tree")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<List<GbShareTreeNodeResponse>> shareTree() {
        return Result.success(catalogShareService.shareTree());
    }

    /**
     * 获取某上级平台已共享的目录 / 通道节点 id（回填前端勾选态）
     */
    @GetMapping("/{id}/shared")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<GbPlatformSharedResponse> getShared(@PathVariable Long id) {
        return Result.success(catalogShareService.getShared(id));
    }

    /**
     * 全量覆盖保存某上级平台的共享目录 / 通道
     */
    @PostMapping("/{id}/shared")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Void> saveShared(@PathVariable Long id, @RequestBody GbPlatformShareSaveRequest request) {
        catalogShareService.saveShared(id, request);
        return Result.success();
    }

    /**
     * 分页查询某上级平台某分组下已挂载的 catalog item（推送目录弹窗"已挂载"列表）
     */
    @PostMapping("/{id}/share-group/{groupId}/shared/page")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<PageResult<GbDeviceChannel>> sharedPage(
            @PathVariable("id") Long id,
            @PathVariable("groupId") Long groupId,
            @RequestBody UnmountedChannelPageRequest req) {
        Page<GbDeviceChannel> page = new Page<>(req.getPage(), req.getPageSize());
        var result = catalogShareService.pageSharedByGroup(id, groupId, page, req.getKeyword());
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords()));
    }

    /**
     * 分页查询某上级平台"未挂载到本组"的通道（推送目录弹窗"添加"picker）
     */
    @PostMapping("/{id}/share-group/{groupId}/unmounted-channels/page")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<PageResult<GbDeviceChannel>> unmountedChannelsPage(
            @PathVariable("id") Long id,
            @PathVariable("groupId") Long groupId,
            @RequestBody UnmountedChannelPageRequest req) {
        Page<GbDeviceChannel> page = new Page<>(req.getPage(), req.getPageSize());
        var result = catalogShareService.pageUnmountedByGroup(id, groupId, page, req.getKeyword());
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords()));
    }

    /**
     * 批量挂载 catalog item 到某分组（推送目录弹窗"添加"确认）。请求体：{@code {"resourceIds": [id1, id2, ...]}}
     */
    @PostMapping("/{id}/share-group/{groupId}/share")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Map<String, Object>> addShares(
            @PathVariable("id") Long id,
            @PathVariable("groupId") Long groupId,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> resourceIds = body == null ? null : body.get("resourceIds");
        if (resourceIds == null || resourceIds.isEmpty()) {
            throw new ServiceException("resourceIds 不能为空");
        }
        int n = catalogShareService.addShares(id, groupId, resourceIds);
        return Result.success(Map.of("added", n));
    }

    /**
     * 单条卸载（按 (platform_id, group_id, resource_id) 定位删除）
     */
    @DeleteMapping("/{id}/share-group/{groupId}/share/{resourceId}")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Void> removeShare(
            @PathVariable("id") Long id,
            @PathVariable("groupId") Long groupId,
            @PathVariable("resourceId") Long resourceId) {
        catalogShareService.removeShare(id, groupId, resourceId);
        return Result.success();
    }

    /**
     * 清除某上级平台某分组下的全部挂载关系（管理台树节点"清除本组"按钮）
     */
    @DeleteMapping("/{id}/share-group/{groupId}/share")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Map<String, Object>> clearShares(
            @PathVariable("id") Long id,
            @PathVariable("groupId") Long groupId) {
        int n = catalogShareService.clearShares(id, groupId);
        return Result.success(Map.of("cleared", n));
    }

    /**
     * 清除某上级平台下全部挂载关系（管理台右侧"一键清除"按钮；不分组，按 platform_id 全删）
     */
    @DeleteMapping("/{id}/share")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Map<String, Object>> clearAllSharesByPlatform(@PathVariable("id") Long id) {
        int n = catalogShareService.clearAllSharesByPlatform(id);
        return Result.success(Map.of("cleared", n));
    }

    /**
     * 一键添加所有"未挂载到本组"通道到该分组（管理台"一键添加"按钮）
     */
    @PostMapping("/{id}/share-group/{groupId}/share-all")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Map<String, Object>> addAllShares(
            @PathVariable("id") Long id,
            @PathVariable("groupId") Long groupId) {
        int n = catalogShareService.addAllUnmounted(id, groupId);
        return Result.success(Map.of("added", n));
    }
}
