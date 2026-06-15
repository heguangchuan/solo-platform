package com.solo.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.entity.SysPermission;
import com.solo.request.PermissionExportRequest;
import com.solo.request.PermissionRequest;
import com.solo.response.PermissionExcelResponse;
import com.solo.service.SysPermissionService;
import com.solo.utils.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 权限管理控制器
 * 提供权限列表查询及权限CRUD接口
 */
@RestController
@RequestMapping("/api/system/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final SysPermissionService sysPermissionService;

    /**
     * 获取权限列表（平铺）
     *
     * @return 所有权限列表
     */
    @GetMapping("/list")
    @RequirePermission({"system:permission:list"})
    public Result<List<SysPermission>> getList() {
        return Result.success(sysPermissionService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getSort)));
    }

    /**
     * 导出权限列表
     */
    @PostMapping("/export")
    @RequirePermission({"system:permission:export"})
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
            case 3 -> "接口";
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
     * @param request 权限信息请求体
     * @return 创建成功响应
     */
    @PostMapping
    @RequirePermission({"system:permission:add"})
    public Result<Void> save(@RequestBody PermissionRequest request) {
        sysPermissionService.createPermission(request);
        return Result.success();
    }

    /**
     * 获取权限树形结构
     *
     * @param type 可选的权限类型过滤（1菜单 / 2按钮 / 3接口），不传则返回全量
     * @return 权限树
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
     * @param id      权限ID
     * @param request 权限信息请求体
     * @return 修改成功响应
     */
    @PutMapping("/{id}")
    @RequirePermission({"system:permission:edit"})
    public Result<Void> update(@PathVariable Long id, @RequestBody PermissionRequest request) {
        sysPermissionService.updatePermission(id, request);
        return Result.success();
    }

    /**
     * 批量删除权限
     */
    @DeleteMapping("/batch")
    @RequirePermission({"system:permission:delete"})
    public Result<Void> deleteBatch(@RequestBody java.util.List<Long> ids) {
        sysPermissionService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除权限
     *
     * @param id 权限ID
     * @return 删除成功响应
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:permission:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        sysPermissionService.deletePermission(id);
        return Result.success();
    }
}
