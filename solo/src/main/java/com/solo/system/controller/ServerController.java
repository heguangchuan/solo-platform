package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.system.entity.SysServer;
import com.solo.system.request.ServerPageRequest;
import com.solo.system.request.ServerRequest;
import com.solo.system.service.SysServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 服务器管理
 */
@RestController
@RequestMapping("/api/server")
@RequiredArgsConstructor
public class ServerController {

    private final SysServerService sysServerService;

    /**
     * 分页查询服务器
     */
    @PostMapping("/page")
    @RequirePermission({"server:list"})
    public Result<PageResult<SysServer>> getPage(@RequestBody ServerPageRequest req) {
        Page<SysServer> page = sysServerService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增服务器
     */
    @PostMapping
    @RequirePermission({"server:add"})
    @OperationLog(module = "服务器管理", type = BusinessType.ADD, description = "新增服务器", recordParam = false)
    public Result<Void> save(@RequestBody ServerRequest request) {
        sysServerService.createServer(request);
        return Result.success();
    }

    /**
     * 修改服务器
     *
     * @param id 服务器主键 ID
     */
    @PutMapping("/{id}")
    @RequirePermission({"server:edit"})
    @OperationLog(module = "服务器管理", type = BusinessType.EDIT, description = "修改服务器", recordParam = false)
    public Result<Void> update(@PathVariable Long id, @RequestBody ServerRequest request) {
        sysServerService.updateServer(id, request);
        return Result.success();
    }

    /**
     * 批量删除服务器
     */
    @DeleteMapping("/batch")
    @RequirePermission({"server:delete"})
    @OperationLog(module = "服务器管理", type = BusinessType.DELETE, description = "批量删除服务器")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysServerService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除单个服务器
     *
     * @param id 服务器主键 ID
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"server:delete"})
    @OperationLog(module = "服务器管理", type = BusinessType.DELETE, description = "删除服务器")
    public Result<Void> delete(@PathVariable Long id) {
        sysServerService.deleteServer(id);
        return Result.success();
    }
}
