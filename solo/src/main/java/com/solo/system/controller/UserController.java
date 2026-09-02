package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.system.entity.SysUser;
import com.solo.system.request.UserExportRequest;
import com.solo.system.request.UserPageRequest;
import com.solo.system.request.UserRequest;
import com.solo.system.request.UserRoleAssignRequest;
import com.solo.system.response.UserExcelResponse;
import com.solo.system.service.SysUserService;
import com.solo.common.utils.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 用户管理
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserService sysUserService;

    /**
     * 分页查询用户列表
     */
    @PostMapping("/page")
    @RequirePermission({"system:user:list"})
    public Result<PageResult<SysUser>> getPage(@RequestBody UserPageRequest req) {
        Page<SysUser> page = sysUserService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 导出用户为 Excel
     */
    @PostMapping("/export")
    @RequirePermission({"system:user:export"})
    @OperationLog(module = "用户管理", type = BusinessType.EXPORT, description = "导出用户")
    public void export(@RequestBody UserExportRequest request, HttpServletResponse response) throws IOException {
        List<SysUser> users = queryExportUsers(request);
        List<UserExcelResponse> data = users.stream().map(this::toExcelResponse).toList();
        ExcelUtils.export(response, "用户列表", "用户列表", data, UserExcelResponse.class);
    }

    private List<SysUser> queryExportUsers(UserExportRequest request) {
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            return sysUserService.listByIds(request.getIds());
        }
        request.setPage(1L);
        request.setPageSize(Long.MAX_VALUE);
        return sysUserService.pageQuery(request).getRecords();
    }

    private UserExcelResponse toExcelResponse(SysUser user) {
        UserExcelResponse response = new UserExcelResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setStatus(user.getStatus() != null && user.getStatus() == 1 ? "启用" : "禁用");
        response.setCreateTime(user.getCreateTime() == null ? null : user.getCreateTime().format(DATE_TIME_FORMATTER));
        return response;
    }

    /**
     * 新增用户
     */
    @PostMapping
    @RequirePermission({"system:user:add"})
    @OperationLog(module = "用户管理", type = BusinessType.ADD, description = "新增用户")
    public Result<Void> save(@RequestBody UserRequest request) {
        sysUserService.createUser(request);
        return Result.success();
    }

    /**
     * 修改用户信息
     *
     * @param id 用户主键 ID
     */
    @PutMapping("/{id}")
    @RequirePermission({"system:user:edit"})
    @OperationLog(module = "用户管理", type = BusinessType.EDIT, description = "修改用户")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserRequest request) {
        sysUserService.updateUser(id, request);
        return Result.success();
    }

    /**
     * 切换用户状态
     *
     * @param id 用户主键 ID
     * @param status `0=禁用`、`1=启用`；禁用后该用户无法登录，已登录的会被踢出
     */
    @PutMapping("/{id}/status/{status}")
    @RequirePermission({"system:user:switch"})
    @OperationLog(module = "用户管理", type = BusinessType.EDIT, description = "切换用户状态")
    public Result<Void> switchStatus(@PathVariable Long id, @PathVariable Integer status) {
        sysUserService.switchStatus(id, status);
        return Result.success();
    }

    /**
     * 批量删除用户
     */
    @DeleteMapping("/batch")
    @RequirePermission({"system:user:delete"})
    @OperationLog(module = "用户管理", type = BusinessType.DELETE, description = "批量删除用户")
    public Result<Void> deleteBatch(@RequestBody java.util.List<Long> ids) {
        sysUserService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除单个用户
     *
     * @param id 用户主键 ID
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:user:delete"})
    @OperationLog(module = "用户管理", type = BusinessType.DELETE, description = "删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteUser(id);
        return Result.success();
    }

    /**
     * 查询用户已分配的角色 ID 列表
     *
     * @param userId 用户主键 ID
     */
    @GetMapping("/r2u/{userId}")
    @RequirePermission({"system:user:r2u"})
    public Result<List<String>> listAssignedRoles(@PathVariable Long userId) {
        // 与角色列表里 id（ToStringSerializer 序列化为 string）保持类型一致
        List<String> ids = sysUserService.listRoleIds(userId).stream()
                .map(String::valueOf)
                .toList();
        return Result.success(ids);
    }

    /**
     * 给用户分配角色
     */
    @PostMapping("/r2u")
    @RequirePermission({"system:user:r2u"})
    @OperationLog(module = "用户管理", type = BusinessType.EDIT, description = "用户授权")
    public Result<Void> assignRoles(@RequestBody UserRoleAssignRequest request) {
        sysUserService.assignRoles(request.getUserId(), request.getRoleIds());
        return Result.success();
    }
}
