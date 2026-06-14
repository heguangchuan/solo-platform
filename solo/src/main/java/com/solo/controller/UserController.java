package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysUser;
import com.solo.request.UserPageRequest;
import com.solo.request.UserRequest;
import com.solo.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 * 提供用户列表分页查询、新增、编辑、删除等CRUD接口
 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserService sysUserService;

    /**
     * 分页查询用户列表（POST）
     */
    @PostMapping("/page")
    @RequirePermission({"system:user:list"})
    public Result<PageResult<SysUser>> getPage(@RequestBody UserPageRequest req) {
        Page<SysUser> page = sysUserService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 新增用户
     */
    @PostMapping
    @RequirePermission({"system:user:add"})
    public Result<Void> save(@RequestBody UserRequest request) {
        sysUserService.createUser(request);
        return Result.success();
    }

    /**
     * 修改用户信息
     */
    @PutMapping("/{id}")
    @RequirePermission({"system:user:edit"})
    public Result<Void> update(@PathVariable Long id, @RequestBody UserRequest request) {
        sysUserService.updateUser(id, request);
        return Result.success();
    }

    /**
     * 切换用户状态
     */
    @PutMapping("/{id}/status/{status}")
    @RequirePermission({"system:user:switch"})
    public Result<Void> switchStatus(@PathVariable Long id, @PathVariable Integer status) {
        sysUserService.switchStatus(id, status);
        return Result.success();
    }

    /**
     * 批量删除用户
     */
    @DeleteMapping("/batch")
    @RequirePermission({"system:user:delete"})
    public Result<Void> deleteBatch(@RequestBody java.util.List<Long> ids) {
        sysUserService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:user:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteUser(id);
        return Result.success();
    }
}
