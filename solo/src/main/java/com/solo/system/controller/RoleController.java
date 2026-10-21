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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Tag(name = "角色管理", description = "角色管理")
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysRoleService sysRoleService;

    @Operation(summary = "分页查询角色列表")
    @PostMapping("/page")
    @RequirePermission({"system:role:list"})
    public Result<PageResult<SysRole>> getPage(@RequestBody RolePageRequest req) {
        Page<SysRole> page = sysRoleService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "导出角色为 Excel")
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

    @Operation(summary = "新增角色")
    @PostMapping
    @RequirePermission({"system:role:add"})
    @OperationLog(module = "角色管理", type = BusinessType.ADD, description = "新增角色")
    public Result<Void> save(@RequestBody RoleRequest request) {
        sysRoleService.createRole(request);
        return Result.success();
    }

    @Operation(summary = "修改角色信息")
    @Parameter(name = "id", description = "角色主键 ID", in = ParameterIn.PATH)
    @PutMapping("/{id}")
    @RequirePermission({"system:role:edit"})
    @OperationLog(module = "角色管理", type = BusinessType.EDIT, description = "修改角色")
    public Result<Void> update(@PathVariable Long id, @RequestBody RoleRequest request) {
        sysRoleService.updateRole(id, request);
        return Result.success();
    }

    @Operation(summary = "批量删除角色")
    @DeleteMapping("/batch")
    @RequirePermission({"system:role:delete"})
    @OperationLog(module = "角色管理", type = BusinessType.DELETE, description = "批量删除角色")
    public Result<Void> deleteBatch(@RequestBody java.util.List<Long> ids) {
        sysRoleService.deleteBatch(ids);
        return Result.success();
    }

    @Operation(summary = "全量启用角色列表")
    @GetMapping("/list")
    @RequirePermission({"system:role:list"})
    public Result<List<SysRole>> list() {
        List<SysRole> roles = sysRoleService.lambdaQuery()
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId)
                .list();
        return Result.success(roles);
    }

    @Operation(summary = "删除单个角色")
    @Parameter(name = "id", description = "角色主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"system:role:delete"})
    @OperationLog(module = "角色管理", type = BusinessType.DELETE, description = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.success();
    }

    @Operation(summary = "查询角色已分配的权限 ID 列表")
    @Parameter(name = "roleId", description = "角色主键 ID", in = ParameterIn.PATH)
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

    @Operation(summary = "给角色分配权限")
    @PostMapping("/p2r")
    @RequirePermission({"system:role:p2r"})
    @OperationLog(module = "角色管理", type = BusinessType.EDIT, description = "角色授权")
    public Result<Void> assignPermissions(@RequestBody RolePermissionAssignRequest request) {
        sysRoleService.assignPermissions(request.getRoleId(), request.getPermissionIds());
        return Result.success();
    }
}
