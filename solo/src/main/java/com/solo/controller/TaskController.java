package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysTask;
import com.solo.entity.SysTaskLog;
import com.solo.request.TaskLogPageRequest;
import com.solo.request.TaskPageRequest;
import com.solo.request.TaskRequest;
import com.solo.service.SysTaskLogService;
import com.solo.service.SysTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final SysTaskService sysTaskService;
    private final SysTaskLogService sysTaskLogService;

    @PostMapping("/page")
    @RequirePermission({"task:manage:list"})
    public Result<PageResult<SysTask>> getPage(@RequestBody TaskPageRequest req) {
        Page<SysTask> page = sysTaskService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @PostMapping
    @RequirePermission({"task:manage:add"})
    @OperationLog(module = "任务管理", type = BusinessType.ADD, description = "新增任务")
    public Result<Void> save(@RequestBody TaskRequest request) {
        sysTaskService.createTask(request);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission({"task:manage:edit"})
    @OperationLog(module = "任务管理", type = BusinessType.EDIT, description = "修改任务")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskRequest request) {
        sysTaskService.updateTask(id, request);
        return Result.success();
    }

    @DeleteMapping("/batch")
    @RequirePermission({"task:manage:delete"})
    @OperationLog(module = "任务管理", type = BusinessType.DELETE, description = "批量删除任务")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysTaskService.deleteBatch(ids);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission({"task:manage:delete"})
    @OperationLog(module = "任务管理", type = BusinessType.DELETE, description = "删除任务")
    public Result<Void> delete(@PathVariable Long id) {
        sysTaskService.deleteTask(id);
        return Result.success();
    }

    @PutMapping("/{id}/switch/{status}")
    @RequirePermission({"task:manage:switch"})
    @OperationLog(module = "任务管理", type = BusinessType.EDIT, description = "启停任务")
    public Result<Void> switchStatus(@PathVariable Long id, @PathVariable Integer status) {
        sysTaskService.switchTaskStatus(id, status);
        return Result.success();
    }

    @PostMapping("/{id}/run")
    @RequirePermission({"task:manage:run"})
    @OperationLog(module = "任务管理", type = BusinessType.OTHER, description = "立即执行任务")
    public Result<Void> run(@PathVariable Long id) {
        sysTaskService.runTaskImmediately(id);
        return Result.success();
    }

    @PostMapping("/log/page")
    @RequirePermission({"task:log:list"})
    public Result<PageResult<SysTaskLog>> logPage(@RequestBody TaskLogPageRequest req) {
        Page<SysTaskLog> page = sysTaskLogService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }
}
