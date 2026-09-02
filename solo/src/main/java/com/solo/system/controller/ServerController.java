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
 * 服务器管理接口（被纳管主机）
 *
 * <p>维护被纳管的目标主机元数据（IP、SSH 端口、登录方式、凭据），供
 * "服务器文件管理"模块（{@link ServerFileController}）和 Web SSH 终端（WebSocket）使用。</p>
 *
 * <h3>Web SSH 终端</h3>
 * <p>前端打开终端页时建议建立 WebSocket 连接：<br>
 * {@code ws://<host>/api/ws/terminal/{serverId}?token=<accessToken>}<br>
 * 走与服务器相同的 SSH 凭据登录。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST   /api/server/page} — 分页查询服务器</li>
 *   <li>{@code POST   /api/server} — 新增服务器</li>
 *   <li>{@code PUT    /api/server/{id}} — 修改服务器</li>
 *   <li>{@code DELETE /api/server/batch} — 批量删除</li>
 *   <li>{@code DELETE /api/server/{id}} — 删除单个</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/server")
@RequiredArgsConstructor
public class ServerController {

    private final SysServerService sysServerService;

    /**
     * 分页查询服务器
     *
     * <p>返回 {@code { total, rows }}；按 {@code serverName} / {@code host} / {@code status} 过滤。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"server:list"})
    public Result<PageResult<SysServer>> getPage(@RequestBody ServerPageRequest req) {
        Page<SysServer> page = sysServerService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增服务器
     *
     * <p>{@code authType=password} 时 {@code password} 必填；{@code authType=privateKey} 时 {@code privateKey} 必填。
     * 凭据落库加密，不会在响应里返回原文（再次修改时也要重传）。</p>
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
     * <p>{@code {id}} 是服务器主键 ID；如要更新凭据（密码 / 私钥）请在请求体里重传。</p>
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
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"server:delete"})
    @OperationLog(module = "服务器管理", type = BusinessType.DELETE, description = "删除服务器")
    public Result<Void> delete(@PathVariable Long id) {
        sysServerService.deleteServer(id);
        return Result.success();
    }
}
