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

@Tag(name = "上级平台管理", description = "上级平台管理")
@RestController
@RequestMapping("/api/gb28181/parent-platform")
@RequiredArgsConstructor
public class GbParentPlatformController {

    private final GbParentPlatformService gbParentPlatformService;
    private final CatalogShareService catalogShareService;
    private final Gb28181Properties gb28181Properties;

    @Operation(summary = "分页查询上级平台配置")
    @PostMapping("/page")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<PageResult<GbParentPlatform>> page(@RequestBody GbParentPlatformPageRequest request) {
        Page<GbParentPlatform> page = gbParentPlatformService.pageQuery(request);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "新增上级平台配置")
    @PostMapping("/save")
    @RequirePermission({"gb28181:parent-platform:add"})
    public Result<Void> save(@RequestBody GbParentPlatform platform) {
        gbParentPlatformService.create(platform);
        return Result.success();
    }

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

    @Operation(summary = "批量删除上级平台配置")
    @PostMapping("/batch/delete")
    @RequirePermission({"gb28181:parent-platform:delete"})
    public Result<Void> batchDelete(@RequestBody GbParentPlatformBatchDeleteRequest request) {
        gbParentPlatformService.deleteBatch(request.getIds());
        return Result.success();
    }

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


    @Operation(summary = "获取可共享的资源目录树")
    @GetMapping("/share-tree")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<List<GbShareTreeNodeResponse>> shareTree() {
        return Result.success(catalogShareService.shareTree());
    }

    @Operation(summary = "获取某上级平台已共享的目录/通道节点 id")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @GetMapping("/{id}/shared")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<GbPlatformSharedResponse> getShared(@PathVariable Long id) {
        return Result.success(catalogShareService.getShared(id));
    }

    @Operation(summary = "全量覆盖保存某上级平台的共享目录/通道")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @PostMapping("/{id}/shared")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Void> saveShared(@PathVariable Long id, @RequestBody GbPlatformShareSaveRequest request) {
        catalogShareService.saveShared(id, request);
        return Result.success();
    }

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


    @Operation(summary = "清除某上级平台下全部挂载关系")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}/share")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Map<String, Object>> clearAllSharesByPlatform(@PathVariable("id") Long id) {
        int n = catalogShareService.clearAllSharesByPlatform(id);
        return Result.success(Map.of("cleared", n));
    }


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


    @Operation(summary = "主动推送目录给上级平台")
    @Parameter(name = "id", description = "上级平台主键 ID", in = ParameterIn.PATH)
    @PostMapping("/{id}/push-catalog")
    @RequirePermission({"gb28181:parent-platform:edit"})
    public Result<Void> pushCatalog(@PathVariable("id") Long id) {
        catalogShareService.pushCatalog(id);
        return Result.success();
    }
}
