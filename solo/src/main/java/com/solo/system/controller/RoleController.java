package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.system.entity.SysRole;
import com.solo.system.request.RoleExportRequest;
import com.solo.system.request.RolePageRequest;
import com.solo.system.request.RolePermissionAssignRequest;
import com.solo.system.request.RoleRequest;
import com.solo.system.response.RoleExcelResponse;
import com.solo.system.service.SysRoleService;
import com.solo.common.utils.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 角色管理接口
 *
 * <p>维护角色（{@code sys_role}），提供分页、CRUD、导出、角色授权（{@code p2r}）。
 * 一个角色对应多组权限；{@code /p2r} 系列接口用于分配/查询该角色下挂的权限 ID。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST   /api/role/page} — 分页查询角色</li>
 *   <li>{@code POST   /api/role/export} — 导出角色为 Excel</li>
 *   <li>{@code POST   /api/role} — 新增角色</li>
 *   <li>{@code PUT    /api/role/{id}} — 修改角色</li>
 *   <li>{@code DELETE /api/role/batch} — 批量删除</li>
 *   <li>{@code GET    /api/role/list} — 全量启用角色（用于选择）</li>
 *   <li>{@code DELETE /api/role/{id}} — 删除单个</li>
 *   <li>{@code GET    /api/role/p2r/{roleId}} — 查询角色已分配的权限 ID</li>
 *   <li>{@code POST   /api/role/p2r} — 给角色分配权限</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysRoleService sysRoleService;

    /**
     * 分页查询角色列表
     *
     * <p>返回 {@code { total, rows }}；按 {@code roleCode} / {@code roleName} 过滤。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"system:role:list"})
    public Result<PageResult<SysRole>> getPage(@RequestBody RolePageRequest req) {
        Page<SysRole> page = sysRoleService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 导出角色为 Excel
     *
     * <p>响应为二进制流；{@code ids} 非空时按 ID 集合导出，否则按分页条件全量导出。</p>
     */
    @PostMapping("/export")
    @RequirePermission({"system:role:export"})
    @OperationLog(module = "角色管理", type = BusinessType.EXPORT, description = "导出角色")
    public void export(@RequestBody RoleExportRequest request, HttpServletResponse response) throws IOException {
        List<SysRole> roles = queryExportRoles(request);
        List<RoleExcelResponse> data = roles.stream().map(this::toExcelResponse).toList();
        ExcelUtils.export(response, "角色列表", "角色列表", data, RoleExcelResponse.class);
    }

    private List<SysRole> queryExportRoles(RoleExportRequest request) {
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            return sysRoleService.listByIds(request.getIds());
        }
        request.setPage(1L);
        request.setPageSize(Long.MAX_VALUE);
        return sysRoleService.pageQuery(request).getRecords();
    }

    private RoleExcelResponse toExcelResponse(SysRole role) {
        RoleExcelResponse response = new RoleExcelResponse();
        response.setId(role.getId());
        response.setRoleCode(role.getRoleCode());
        response.setRoleName(role.getRoleName());
        response.setDescription(role.getDescription());
        response.setStatus(role.getStatus() != null && role.getStatus() == 1 ? "启用" : "禁用");
        response.setCreateTime(role.getCreateTime() == null ? null : role.getCreateTime().format(DATE_TIME_FORMATTER));
        return response;
    }

    /**
     * 新增角色
     *
     * <p>{@code roleCode} 全局唯一；新增后给"用户授权"下拉用。</p>
     */
    @PostMapping
    @RequirePermission({"system:role:add"})
    @OperationLog(module = "角色管理", type = BusinessType.ADD, description = "新增角色")
    public Result<Void> save(@RequestBody RoleRequest request) {
        sysRoleService.createRole(request);
        return Result.success();
    }

    /**
     * 修改角色信息
     *
     * <p>{@code {id}} 是角色主键 ID。</p>
     */
    @PutMapping("/{id}")
    @RequirePermission({"system:role:edit"})
    @OperationLog(module = "角色管理", type = BusinessType.EDIT, description = "修改角色")
    public Result<Void> update(@PathVariable Long id, @RequestBody RoleRequest request) {
        sysRoleService.updateRole(id, request);
        return Result.success();
    }

    /**
     * 批量删除角色
     */
    @DeleteMapping("/batch")
    @RequirePermission({"system:role:delete"})
    @OperationLog(module = "角色管理", type = BusinessType.DELETE, description = "批量删除角色")
    public Result<Void> deleteBatch(@RequestBody java.util.List<Long> ids) {
        sysRoleService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 全量启用角色列表
     *
     * <p>仅 {@code status=1} 的角色；按 ID 升序；用于"用户授权"弹窗的多选下拉。</p>
     */
    @GetMapping("/list")
    @RequirePermission({"system:role:list"})
    public Result<List<SysRole>> list() {
        List<SysRole> roles = sysRoleService.lambdaQuery()
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId)
                .list();
        return Result.success(roles);
    }

    /**
     * 删除单个角色
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:role:delete"})
    @OperationLog(module = "角色管理", type = BusinessType.DELETE, description = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.success();
    }

    /**
     * 查询角色已分配的权限 ID 列表
     *
     * <p>ID 序列化为字符串返回，与 {@code /api/permission/tree} 节点 ID 类型一致，
     * 前端可直接 {@code treeRef.setCheckedKeys(ids)} 渲染勾选状态。</p>
     */
    @GetMapping("/p2r/{roleId}")
    @RequirePermission({"system:role:p2r"})
    public Result<List<String>> listAssignedPermissions(@PathVariable Long roleId) {
        // 与权限树里 id（ToStringSerializer 序列化为 string）保持类型一致，
        // 前端可直接 setCheckedKeys，无需再次 String() 转换
        List<String> ids = sysRoleService.listPermissionIds(roleId).stream()
                .map(String::valueOf)
                .toList();
        return Result.success(ids);
    }

    /**
     * 给角色分配权限
     *
     * <p>全量替换该角色已有权限集：传入的 {@code permissionIds} 即为最新全量，不在其中的会被取消。
     * 调用时机一般紧跟在 {@code /p2r/{roleId}} 拉取之后、用户在树上勾选完之后。</p>
     */
    @PostMapping("/p2r")
    @RequirePermission({"system:role:p2r"})
    @OperationLog(module = "角色管理", type = BusinessType.EDIT, description = "角色授权")
    public Result<Void> assignPermissions(@RequestBody RolePermissionAssignRequest request) {
        sysRoleService.assignPermissions(request.getRoleId(), request.getPermissionIds());
        return Result.success();
    }
}
