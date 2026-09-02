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
 * 权限管理
 */
@RestController
@RequestMapping("/api/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final SysPermissionService sysPermissionService;

    /**
     * 获取权限列表
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
     * @param type 可选过滤；`1=菜单`、`2=按钮`，不传返回全量
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
     * @param id 权限主键 ID
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
     *
     * @param id 权限主键 ID
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:permission:delete"})
    @OperationLog(module = "权限管理", type = BusinessType.DELETE, description = "删除权限")
    public Result<Void> delete(@PathVariable Long id) {
        sysPermissionService.deletePermission(id);
        return Result.success();
    }
}
