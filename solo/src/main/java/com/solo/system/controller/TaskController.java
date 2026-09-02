package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.system.entity.SysTask;
import com.solo.system.entity.SysTaskLog;
import com.solo.system.request.TaskLogPageRequest;
import com.solo.system.request.TaskPageRequest;
import com.solo.system.request.TaskRequest;
import com.solo.system.service.SysTaskLogService;
import com.solo.system.service.SysTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 动态定时任务管理
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final SysTaskService sysTaskService;
    private final SysTaskLogService sysTaskLogService;

    /**
     * 分页查询任务
     */
    @PostMapping("/page")
    @RequirePermission({"task:manage:list"})
    public Result<PageResult<SysTask>> getPage(@RequestBody TaskPageRequest req) {
        Page<SysTask> page = sysTaskService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增任务
     */
    @PostMapping
    @RequirePermission({"task:manage:add"})
    @OperationLog(module = "任务管理", type = BusinessType.ADD, description = "新增任务")
    public Result<Void> save(@RequestBody TaskRequest request) {
        sysTaskService.createTask(request);
        return Result.success();
    }

    /**
     * 修改任务
     *
     * @param id 任务主键 ID
     */
    @PutMapping("/{id}")
    @RequirePermission({"task:manage:edit"})
    @OperationLog(module = "任务管理", type = BusinessType.EDIT, description = "修改任务")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskRequest request) {
        sysTaskService.updateTask(id, request);
        return Result.success();
    }

    /**
     * 批量删除任务
     */
    @DeleteMapping("/batch")
    @RequirePermission({"task:manage:delete"})
    @OperationLog(module = "任务管理", type = BusinessType.DELETE, description = "批量删除任务")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysTaskService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除单个任务
     *
     * @param id 任务主键 ID
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"task:manage:delete"})
    @OperationLog(module = "任务管理", type = BusinessType.DELETE, description = "删除任务")
    public Result<Void> delete(@PathVariable Long id) {
        sysTaskService.deleteTask(id);
        return Result.success();
    }

    /**
     * 启停任务
     *
     * @param id 任务主键 ID
     * @param status `0=停用`、`1=启用`；停用后即使 cron 到点也不会触发
     */
    @PutMapping("/{id}/switch/{status}")
    @RequirePermission({"task:manage:switch"})
    @OperationLog(module = "任务管理", type = BusinessType.EDIT, description = "启停任务")
    public Result<Void> switchStatus(@PathVariable Long id, @PathVariable Integer status) {
        sysTaskService.switchTaskStatus(id, status);
        return Result.success();
    }

    /**
     * 立即执行一次任务
     *
     * @param id 任务主键 ID
     */
    @PostMapping("/{id}/run")
    @RequirePermission({"task:manage:run"})
    @OperationLog(module = "任务管理", type = BusinessType.OTHER, description = "立即执行任务")
    public Result<Void> run(@PathVariable Long id) {
        sysTaskService.runTaskImmediately(id);
        return Result.success();
    }

    /**
     * 分页查询任务执行日志
     */
    @PostMapping("/log/page")
    @RequirePermission({"task:log:list"})
    public Result<PageResult<SysTaskLog>> logPage(@RequestBody TaskLogPageRequest req) {
        Page<SysTaskLog> page = sysTaskLogService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }
}
