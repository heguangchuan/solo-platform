package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysRole;
import com.solo.request.RolePageRequest;
import com.solo.request.RoleRequest;
import com.solo.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 角色管理控制器
 * 提供角色列表分页查询、新增、编辑、删除等CRUD接口
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleService sysRoleService;

    /**
     * 分页查询角色列表（POST）
     */
    @PostMapping("/page")
    @RequirePermission({"system:role:list"})
    public Result<PageResult<SysRole>> getPage(@RequestBody RolePageRequest req) {
        Page<SysRole> page = sysRoleService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增角色
     */
    @PostMapping
    @RequirePermission({"system:role:add"})
    public Result<Void> save(@RequestBody RoleRequest request) {
        sysRoleService.createRole(request.getRoleCode(), request.getRoleName(),
                request.getDescription(), request.getStatus());
        return Result.success();
    }

    /**
     * 修改角色信息
     */
    @PutMapping("/{id}")
    @RequirePermission({"system:role:edit"})
    public Result<Void> update(@PathVariable Long id, @RequestBody RoleRequest request) {
        sysRoleService.updateRole(id, request.getRoleName(), request.getDescription(), request.getStatus());
        return Result.success();
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:role:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.success();
    }
}
