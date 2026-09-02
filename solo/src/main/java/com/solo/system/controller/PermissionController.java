package com.solo.system.controller;

import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.Result;
import com.solo.system.entity.SysPermission;
import com.solo.system.request.PermissionExportRequest;
import com.solo.system.request.PermissionRequest;
import com.solo.system.response.PermissionExcelResponse;
import com.solo.system.service.SysPermissionService;
import com.solo.common.utils.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 权限管理接口
 *
 * <p>维护菜单 / 按钮两类节点，提供平铺列表、树形结构、CRUD 与 Excel 导出。
 * {@code type}：{@code 1=菜单}、{@code 2=按钮}；{@code parentId=0} 表示顶级。
 * 树形结构（{@code /tree}）按 {@code parentId} 自动嵌套成 {@code children} 字段。</p>
 *
 * <p>权限编码 {@code permissionCode} 是按钮级控制的依据（与 {@code v-permission} 指令对应）。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code GET    /api/permission/list} — 平铺权限列表（按 sort 升序）</li>
 *   <li>{@code POST   /api/permission/export} — 导出权限为 Excel</li>
 *   <li>{@code POST   /api/permission} — 新增权限</li>
 *   <li>{@code GET    /api/permission/tree} — 权限树（可选 type 过滤）</li>
 *   <li>{@code PUT    /api/permission/{id}} — 修改权限</li>
 *   <li>{@code DELETE /api/permission/batch} — 批量删除</li>
 *   <li>{@code DELETE /api/permission/{id}} — 删除单个</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final SysPermissionService sysPermissionService;

    /**
     * 获取权限列表（平铺）
     *
     * <p>不分页，按 {@code sort} 升序；适合做权限分配弹窗的源数据。</p>
     */
    @GetMapping("/list")
    @RequirePermission({"system:permission:list"})
    public Result<List<SysPermission>> getList() {
        return Result.success(sysPermissionService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getSort)));
    }

    /**
     * 导出权限为 Excel
     *
     * <p>响应为二进制流；{@code ids} 非空时按 ID 集合导出，否则按下方筛选条件全量导出。
     * {@code type} 列在 Excel 中以"菜单/按钮/未知"文本呈现。</p>
     */
    @PostMapping("/export")
    @RequirePermission({"system:permission:export"})
    @OperationLog(module = "权限管理", type = BusinessType.EXPORT, description = "导出权限")
    public void export(@RequestBody PermissionExportRequest request, HttpServletResponse response) throws IOException {
        List<SysPermission> permissions = queryExportPermissions(request);
        List<PermissionExcelResponse> data = permissions.stream().map(this::toExcelResponse).toList();
        ExcelUtils.export(response, "权限列表", "权限列表", data, PermissionExcelResponse.class);
    }

    private List<SysPermission> queryExportPermissions(PermissionExportRequest request) {
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            return sysPermissionService.listByIds(request.getIds());
        }
        return sysPermissionService.lambdaQuery()
                .like(request.getPermissionName() != null && !request.getPermissionName().isBlank(), SysPermission::getPermissionName, request.getPermissionName())
                .like(request.getPermissionCode() != null && !request.getPermissionCode().isBlank(), SysPermission::getPermissionCode, request.getPermissionCode())
                .eq(request.getType() != null, SysPermission::getType, request.getType())
                .eq(request.getStatus() != null, SysPermission::getStatus, request.getStatus())
                .orderByAsc(SysPermission::getSort)
                .list();
    }

    private PermissionExcelResponse toExcelResponse(SysPermission permission) {
        PermissionExcelResponse response = new PermissionExcelResponse();
        response.setId(permission.getId());
        response.setPermissionName(permission.getPermissionName());
        response.setPermissionCode(permission.getPermissionCode());
        response.setUrl(permission.getUrl());
        response.setType(switch (permission.getType() == null ? 0 : permission.getType()) {
            case 1 -> "菜单";
            case 2 -> "按钮";
            default -> "未知";
        });
        response.setParentId(permission.getParentId());
        response.setSort(permission.getSort());
        response.setStatus(permission.getStatus() != null && permission.getStatus() == 1 ? "启用" : "禁用");
        return response;
    }

    /**
     * 新增权限
     *
     * <p>{@code permissionCode} 全局唯一，新增后给 {@code v-permission} 指令使用。
     * 字段含义见 {@link PermissionRequest}。</p>
     */
    @PostMapping
    @RequirePermission({"system:permission:add"})
    @OperationLog(module = "权限管理", type = BusinessType.ADD, description = "新增权限")
    public Result<Void> save(@RequestBody PermissionRequest request) {
        sysPermissionService.createPermission(request);
        return Result.success();
    }

    /**
     * 获取权限树形结构
     *
     * <p>可选按 {@code type} 过滤（{@code 1=菜单}、{@code 2=按钮}），不传返回全量；
     * 已按 {@code parentId} 嵌套成 {@code children} 字段，前端可直接递归渲染。</p>
     */
    @GetMapping("/tree")
    @RequirePermission({"system:permission:list"})
    public Result<List<SysPermission>> getTree(@RequestParam(required = false) Integer type) {
        List<SysPermission> all = sysPermissionService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                        .eq(type != null, SysPermission::getType, type)
                        .orderByAsc(SysPermission::getSort));
        return Result.success(sysPermissionService.buildTree(all, 0L));
    }

    /**
     * 修改权限信息
     *
     * <p>{@code {id}} 是权限主键 ID。</p>
     */
    @PutMapping("/{id}")
    @RequirePermission({"system:permission:edit"})
    @OperationLog(module = "权限管理", type = BusinessType.EDIT, description = "修改权限")
    public Result<Void> update(@PathVariable Long id, @RequestBody PermissionRequest request) {
        sysPermissionService.updatePermission(id, request);
        return Result.success();
    }

    /**
     * 批量删除权限
     *
     * <p>注意：删除父权限会同时移除其下子权限（前端调用前可弹二次确认）。</p>
     */
    @DeleteMapping("/batch")
    @RequirePermission({"system:permission:delete"})
    @OperationLog(module = "权限管理", type = BusinessType.DELETE, description = "批量删除权限")
    public Result<Void> deleteBatch(@RequestBody java.util.List<Long> ids) {
        sysPermissionService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除单个权限
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:permission:delete"})
    @OperationLog(module = "权限管理", type = BusinessType.DELETE, description = "删除权限")
    public Result<Void> delete(@PathVariable Long id) {
        sysPermissionService.deletePermission(id);
        return Result.success();
    }
}
