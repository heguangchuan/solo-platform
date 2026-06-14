package com.solo.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.entity.SysPermission;
import com.solo.request.PermissionRequest;
import com.solo.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
     * 新增权限
     *
     * @param request 权限信息请求体
     * @return 创建成功响应
     */
    @PostMapping
    @RequirePermission({"system:permission:list"})
    public Result<Void> save(@RequestBody PermissionRequest request) {
        sysPermissionService.createPermission(request.getPermissionName(), request.getPermissionCode(),
                request.getUrl(), request.getType(), request.getParentId(), request.getSort(),
                request.getIcon(), request.getStatus());
        return Result.success();
    }

    /**
     * 获取权限树形结构
     *
     * @return 权限树
     */
    @GetMapping("/tree")
    @RequirePermission({"system:permission:list"})
    public Result<List<SysPermission>> getTree() {
        List<SysPermission> all = sysPermissionService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
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
    @RequirePermission({"system:permission:list"})
    public Result<Void> update(@PathVariable Long id, @RequestBody PermissionRequest request) {
        sysPermissionService.updatePermission(id, request.getPermissionName(), request.getPermissionCode(),
                request.getUrl(), request.getType(), request.getParentId(), request.getSort(),
                request.getIcon(), request.getStatus());
        return Result.success();
    }

    /**
     * 删除权限
     *
     * @param id 权限ID
     * @return 删除成功响应
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:permission:list"})
    public Result<Void> delete(@PathVariable Long id) {
        sysPermissionService.deletePermission(id);
        return Result.success();
    }
}
