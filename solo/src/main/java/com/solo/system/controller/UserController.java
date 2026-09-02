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
 * 用户管理接口
 *
 * <p>维护系统用户，提供分页、CRUD、导出、启停、用户授权（{@code r2u}）。
 * 一个用户可挂多组角色；{@code /r2u} 系列接口用于分配/查询该用户下挂的角色 ID。</p>
 *
 * <h3>密码维护</h3>
 * <p>本接口不含密码字段。新增用户的默认密码规则见 {@code username@123456}（按用户名生成）；
 * 修改密码走 {@link AuthController} 的 {@code /api/auth/password} 或首次登录强制改密流程。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST   /api/user/page} — 分页查询用户</li>
 *   <li>{@code POST   /api/user/export} — 导出用户为 Excel</li>
 *   <li>{@code POST   /api/user} — 新增用户</li>
 *   <li>{@code PUT    /api/user/{id}} — 修改用户</li>
 *   <li>{@code PUT    /api/user/{id}/status/{status}} — 切换用户状态</li>
 *   <li>{@code DELETE /api/user/batch} — 批量删除</li>
 *   <li>{@code DELETE /api/user/{id}} — 删除单个</li>
 *   <li>{@code GET    /api/user/r2u/{userId}} — 查询用户已分配的角色 ID</li>
 *   <li>{@code POST   /api/user/r2u} — 给用户分配角色</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserService sysUserService;

    /**
     * 分页查询用户列表
     *
     * <p>返回 {@code { total, rows }}；按 {@code username} / {@code nickname} / {@code status} 过滤。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"system:user:list"})
    public Result<PageResult<SysUser>> getPage(@RequestBody UserPageRequest req) {
        Page<SysUser> page = sysUserService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 导出用户为 Excel
     *
     * <p>响应为二进制流；{@code ids} 非空时按 ID 集合导出，否则按分页条件全量导出。
     * <b>不会</b>导出密码字段。</p>
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
     *
     * <p>{@code username} 全局唯一，新增后不可修改；密码由后端按 {@code username@123456} 默认值初始化，
     * 强制改密流程会在用户首次登录时拦下。</p>
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
     * <p>{@code {id}} 是用户主键 ID；{@code username} 编辑时不可修改。
     * 不含密码字段——改密见 {@code POST /api/auth/password}。</p>
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
     * <p>{@code status}：{@code 0=禁用}、{@code 1=启用}；禁用后该用户无法登录，已登录的会被踢出。</p>
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
     * <p>ID 序列化为字符串返回，与角色列表节点 ID 类型一致，前端可直接绑定到多选控件。</p>
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
     *
     * <p>全量替换该用户已有角色集：传入的 {@code roleIds} 即为最新全量，不在其中的会被取消。</p>
     */
    @PostMapping("/r2u")
    @RequirePermission({"system:user:r2u"})
    @OperationLog(module = "用户管理", type = BusinessType.EDIT, description = "用户授权")
    public Result<Void> assignRoles(@RequestBody UserRoleAssignRequest request) {
        sysUserService.assignRoles(request.getUserId(), request.getRoleIds());
        return Result.success();
    }
}
