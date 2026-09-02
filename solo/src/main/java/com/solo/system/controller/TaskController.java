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
 * 动态定时任务接口
 *
 * <p>对系统内可调度的定时任务做 CRUD + 启停 + 立即执行 + 执行日志查询。
 * 任务由后端以 cron 调度执行，<b>不返回 taskId 之类的句柄</b>——前端按主键 ID 操作即可。
 * 执行进度看 {@code /api/task/log/page} 任务执行日志。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST   /api/task/page} — 分页查询任务</li>
 *   <li>{@code POST   /api/task} — 新增任务</li>
 *   <li>{@code PUT    /api/task/{id}} — 修改任务</li>
 *   <li>{@code DELETE /api/task/batch} — 批量删除</li>
 *   <li>{@code DELETE /api/task/{id}} — 删除单个</li>
 *   <li>{@code PUT    /api/task/{id}/switch/{status}} — 启停任务（{@code 0/1}）</li>
 *   <li>{@code POST   /api/task/{id}/run} — 立即执行一次（不等 cron）</li>
 *   <li>{@code POST   /api/task/log/page} — 分页查询任务执行日志</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final SysTaskService sysTaskService;
    private final SysTaskLogService sysTaskLogService;

    /**
     * 分页查询任务
     *
     * <p>返回 {@code { total, rows }}；按 {@code taskName} / {@code taskCode} / {@code status} 过滤。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"task:manage:list"})
    public Result<PageResult<SysTask>> getPage(@RequestBody TaskPageRequest req) {
        Page<SysTask> page = sysTaskService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增任务
     *
     * <p>{@code cron} 7 位 Quartz 风格；{@code handlerName} 必须是后端注册过的执行器标识，
     * 列表里能让用户选；{@code taskParams} 为 JSON 字符串，透传给执行器。</p>
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
     * <p>{@code {id}} 是任务主键 ID。</p>
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
     * <p>{@code status}：{@code 0=停用}、{@code 1=启用}；停用后即使 cron 到点也不会触发。</p>
     */
    @PutMapping("/{id}/switch/{status}")
    @RequirePermission({"task:manage:switch"})
    @OperationLog(module = "任务管理", type = BusinessType.EDIT, description = "启停任务")
    public Result<Void> switchStatus(@PathVariable Long id, @PathVariable Integer status) {
        sysTaskService.switchTaskStatus(id, status);
        return Result.success();
    }

    /**
     * 立即执行一次任务（不等 cron）
     *
     * <p>同步返回即表示"已派发"，不保证此时已执行完成；执行结果在
     * {@code /api/task/log/page} 按 {@code taskId} 过滤查看。</p>
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
     *
     * <p>返回 {@code { total, rows }}；按 {@code taskId} / {@code taskName} / {@code taskCode} / {@code resultStatus}（{@code 0=失败}、{@code 1=成功}）过滤。</p>
     */
    @PostMapping("/log/page")
    @RequirePermission({"task:log:list"})
    public Result<PageResult<SysTaskLog>> logPage(@RequestBody TaskLogPageRequest req) {
        Page<SysTaskLog> page = sysTaskLogService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }
}
