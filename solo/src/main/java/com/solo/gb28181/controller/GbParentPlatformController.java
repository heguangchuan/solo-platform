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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 上级平台管理
 */
@Tag(name = "上级平台管理", description = "上级平台管理")
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
    @Operation(summary = "分页查询上级平台配置")
    @PostMapping("/page")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<PageResult<GbParentPlatform>> page(@RequestBody GbParentPlatformPageRequest request) {
        Page<GbParentPlatform> page = gbParentPlatformService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增上级平台配置
     */
    @Operation(summary = "新增上级平台配置")
    @PostMapping("/save")
    @RequirePermission({"gb28181:parent-platform:add"})
    public Result<Void> save(@RequestBody GbParentPlatform platform) {
        gbParentPlatformService.create(platform);
        return Result.success();
    }

    /**
     * 编辑上级平台配置
     */
    @Operation(summary = "编辑上级平台配置")
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
     *
     * @param id 上级平台主键 ID
     */
    @Operation(summary = "删除上级平台配置")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
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
    @Operation(summary = "批量删除上级平台配置")
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:parent-platform:delete"})
    public Result<Void> batchDelete(@RequestBody GbParentPlatformBatchDeleteRequest request) {
        gbParentPlatformService.deleteBatch(request.getIds());
        return Result.success();
    }

    /**
     * 获取本平台国标身份
     */
    @Operation(summary = "获取本平台国标身份")
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
     * 获取可共享的资源目录树
     */
    @Operation(summary = "获取可共享的资源目录树")
    @GetMapping("/share-tree")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<List<GbShareTreeNodeResponse>> shareTree() {
        return Result.success(catalogShareService.shareTree());
    }

    /**
     * 获取某上级平台已共享的目录 / 通道节点 id
     *
     * @param id 上级平台主键 ID
     */
    @Operation(summary = "获取某上级平台已共享的目录/通道节点 id")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @GetMapping("/{id}/shared")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<GbPlatformSharedResponse> getShared(@PathVariable Long id) {
        return Result.success(catalogShareService.getShared(id));
    }

    /**
     * 全量覆盖保存某上级平台的共享目录 / 通道
     *
     * @param id 上级平台主键 ID
     */
    @Operation(summary = "全量覆盖保存某上级平台的共享目录/通道")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @PostMapping("/{id}/shared")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Void> saveShared(@PathVariable Long id, @RequestBody GbPlatformShareSaveRequest request) {
        catalogShareService.saveShared(id, request);
        return Result.success();
    }

    /**
     * 分页查询某上级平台某分组下已挂载的 catalog item
     *
     * @param id 上级平台主键 ID
     * @param groupId 推送分组主键 ID
     */
    @Operation(summary = "分页查询某上级平台某分组下已挂载的 catalog item")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @Parameter(name = "groupId", description = "推送分组主键 ID", in = ParameterIn.PATH)
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
     * 分页查询某上级平台"未挂载到本组"的通道
     *
     * @param id 上级平台主键 ID
     * @param groupId 推送分组主键 ID
     */
    @Operation(summary = "分页查询某上级平台\"未挂载到本组\"的通道")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @Parameter(name = "groupId", description = "推送分组主键 ID", in = ParameterIn.PATH)
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
     * 批量挂载 catalog item 到某分组
     *
     * @param id 上级平台主键 ID
     * @param groupId 推送分组主键 ID
     * @param body 请求体：`{"resourceIds": [id1, id2, ...]`}，resourceIds 不能为空，否则 400
     * @return 响应：`{"added": N`}，N 为实际新增条数
     */
    @Operation(summary = "批量挂载 catalog item 到某分组")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @Parameter(name = "groupId", description = "推送分组主键 ID", in = ParameterIn.PATH)
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
     * 单条卸载
     *
     * @param id 上级平台主键 ID
     * @param groupId 推送分组主键 ID
     * @param resourceId 待卸载的资源主键 ID
     */
    @Operation(summary = "单条卸载")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @Parameter(name = "groupId", description = "推送分组主键 ID", in = ParameterIn.PATH)
    @Parameter(name = "resourceId", description = "待卸载的资源主键 ID", in = ParameterIn.PATH)
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
     * 清除某上级平台某分组下的全部挂载关系
     *
     * @param id 上级平台主键 ID
     * @param groupId 推送分组主键 ID
     * @return 响应：`{"cleared": N`}，N 为实际删除条数
     */
    @Operation(summary = "清除某上级平台某分组下的全部挂载关系")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @Parameter(name = "groupId", description = "推送分组主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}/share-group/{groupId}/share")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Map<String, Object>> clearShares(
            @PathVariable("id") Long id,
            @PathVariable("groupId") Long groupId) {
        int n = catalogShareService.clearShares(id, groupId);
        return Result.success(Map.of("cleared", n));
    }

    /**
     * 清除某上级平台下全部挂载关系
     *
     * @param id 上级平台主键 ID
     * @return 响应：`{"cleared": N`}，N 为实际删除条数
     */
    @Operation(summary = "清除某上级平台下全部挂载关系")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}/share")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Map<String, Object>> clearAllSharesByPlatform(@PathVariable("id") Long id) {
        int n = catalogShareService.clearAllSharesByPlatform(id);
        return Result.success(Map.of("cleared", n));
    }

    /**
     * 一键添加所有"未挂载到本组"通道到该分组
     *
     * @param id 上级平台主键 ID
     * @param groupId 推送分组主键 ID
     * @return 响应：`{"added": N`}，N 为实际新增条数
     */
    @Operation(summary = "一键添加所有\"未挂载到本组\"通道到该分组")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @Parameter(name = "groupId", description = "推送分组主键 ID", in = ParameterIn.PATH)
    @PostMapping("/{id}/share-group/{groupId}/share-all")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Map<String, Object>> addAllShares(
            @PathVariable("id") Long id,
            @PathVariable("groupId") Long groupId) {
        int n = catalogShareService.addAllUnmounted(id, groupId);
        return Result.success(Map.of("added", n));
    }

    /**
     * 主动推送目录给上级平台（手动触发一次 Catalog NOTIFY）
     *
     * @param id 上级平台主键 ID
     */
    @Operation(summary = "主动推送目录给上级平台")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @PostMapping("/{id}/push-catalog")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Void> pushCatalog(@PathVariable("id") Long id) {
        catalogShareService.pushCatalog(id);
        return Result.success();
    }
}
