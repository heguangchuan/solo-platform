package com.solo.gb28181.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.request.GbParentPlatformBatchDeleteRequest;
import com.solo.gb28181.request.GbParentPlatformPageRequest;
import com.solo.gb28181.request.GbPlatformShareSaveRequest;
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
     * 获取本平台 SIP 配置（用于新增上级平台时回显本地编号/IP/端口等默认值）
     */
    @GetMapping("/server-config")
    @RequirePermission({"gb28181:parent-platform:list"})
    public Result<Map<String, Object>> serverConfig() {
        Map<String, Object> config = Map.of(
                "deviceGbId", gb28181Properties.getId() != null ? gb28181Properties.getId() : "",
                "deviceIp", gb28181Properties.getIp() != null ? gb28181Properties.getIp() : "",
                "devicePort", gb28181Properties.getPort(),
                "domain", gb28181Properties.getDomain() != null ? gb28181Properties.getDomain() : ""
        );
        return Result.success(config);
    }

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
}
