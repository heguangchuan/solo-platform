package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysServer;
import com.solo.request.ServerPageRequest;
import com.solo.request.ServerRequest;
import com.solo.service.SysServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 服务器管理控制器
 */
@RestController
@RequestMapping("/api/server")
@RequiredArgsConstructor
public class ServerController {

    private final SysServerService sysServerService;

    @PostMapping("/page")
    @RequirePermission({"system:server:list"})
    public Result<PageResult<SysServer>> getPage(@RequestBody ServerPageRequest req) {
        Page<SysServer> page = sysServerService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @PostMapping
    @RequirePermission({"system:server:add"})
    @OperationLog(module = "服务器管理", type = BusinessType.ADD, description = "新增服务器", recordParam = false)
    public Result<Void> save(@RequestBody ServerRequest request) {
        sysServerService.createServer(request);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission({"system:server:edit"})
    @OperationLog(module = "服务器管理", type = BusinessType.EDIT, description = "修改服务器", recordParam = false)
    public Result<Void> update(@PathVariable Long id, @RequestBody ServerRequest request) {
        sysServerService.updateServer(id, request);
        return Result.success();
    }

    @DeleteMapping("/batch")
    @RequirePermission({"system:server:delete"})
    @OperationLog(module = "服务器管理", type = BusinessType.DELETE, description = "批量删除服务器")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysServerService.deleteBatch(ids);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission({"system:server:delete"})
    @OperationLog(module = "服务器管理", type = BusinessType.DELETE, description = "删除服务器")
    public Result<Void> delete(@PathVariable Long id) {
        sysServerService.deleteServer(id);
        return Result.success();
    }
}
