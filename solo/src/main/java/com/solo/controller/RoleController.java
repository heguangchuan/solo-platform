package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysRole;
import com.solo.request.RoleExportRequest;
import com.solo.request.RolePageRequest;
import com.solo.request.RoleRequest;
import com.solo.response.RoleExcelResponse;
import com.solo.service.SysRoleService;
import com.solo.utils.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 角色管理控制器
 * 提供角色列表分页查询、新增、编辑、删除等CRUD接口
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class RoleController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
     * 导出角色列表
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
     * 查询全部启用角色（用于选择）
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
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:role:delete"})
    @OperationLog(module = "角色管理", type = BusinessType.DELETE, description = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.success();
    }
}
