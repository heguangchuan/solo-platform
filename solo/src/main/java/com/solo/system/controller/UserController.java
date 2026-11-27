package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.system.entity.SysUser;
import com.solo.system.request.UserExportRequest;
import com.solo.system.request.UserPageRequest;
import com.solo.system.request.UserRequest;
import com.solo.system.response.UserExcelResponse;
import com.solo.system.response.UserListVO;
import com.solo.system.response.UserStatusCountVO;
import com.solo.system.service.SysUserService;
import com.solo.common.utils.ExcelUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Tag(name = "用户管理", description = "用户管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long EXPORT_MAX_ROWS = 10_000L;

    private final SysUserService sysUserService;

    @Operation(summary = "分页查询用户列表")
    @PostMapping("/page")
    public Result<PageResult<UserListVO>> getPage(@Valid @RequestBody UserPageRequest req) {
        Page<SysUser> page = sysUserService.pageQuery(req);
        List<UserListVO> rows = page.getRecords().stream().map(this::toListResponse).toList();
        return Result.success(new PageResult<>(page.getTotal(), rows));
    }

    @Operation(summary = "按状态统计用户数")
    @GetMapping("/status-count")
    public Result<List<UserStatusCountVO>> statusCount() {
        return Result.success(sysUserService.statusCount());
    }

    @Operation(summary = "导出用户为 Excel")
    @PostMapping("/export")
    public void export(@Valid @RequestBody UserExportRequest request, HttpServletResponse response) throws IOException {
        List<SysUser> users = queryExportUsers(request);
        List<UserExcelResponse> data = users.stream().map(this::toExcelResponse).toList();
        ExcelUtils.export(response, "用户列表", "用户列表", data, UserExcelResponse.class);
    }

    private List<SysUser> queryExportUsers(UserExportRequest request) {
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            return sysUserService.listByIds(request.getIds());
        }
        request.setPage(1L);
        request.setPageSize(EXPORT_MAX_ROWS + 1);
        Page<SysUser> page = sysUserService.pageQuery(request);
        if (page.getTotal() > EXPORT_MAX_ROWS) {
            throw new ServiceException("单次导出不能超过 " + EXPORT_MAX_ROWS + " 条，请缩小筛选范围");
        }
        return page.getRecords();
    }

    private UserListVO toListResponse(SysUser user) {
        UserListVO response = new UserListVO();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setAvatar(user.getAvatar());
        response.setStatus(user.getStatus());
        response.setCreateTime(user.getCreateTime());
        response.setUpdateTime(user.getUpdateTime());
        return response;
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

    @Operation(summary = "新增用户")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody UserRequest request) {
        sysUserService.createUser(request);
        return Result.success();
    }

    @Operation(summary = "修改用户信息")
    @Parameter(name = "id", description = "用户主键 ID", in = ParameterIn.PATH)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        sysUserService.updateUser(id, request);
        return Result.success();
    }

    @Operation(summary = "切换用户状态")
    @Parameter(name = "id", description = "用户主键 ID", in = ParameterIn.PATH)
    @Parameter(name = "status", description = "0=禁用、1=启用；禁用后该用户无法登录，已登录的会被踢出", in = ParameterIn.PATH)
    @PutMapping("/{id}/status/{status}")
    public Result<Void> switchStatus(@PathVariable Long id, @PathVariable Integer status) {
        sysUserService.switchStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "批量删除用户")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody java.util.List<Long> ids) {
        sysUserService.deleteBatch(ids);
        return Result.success();
    }

    @Operation(summary = "删除单个用户")
    @Parameter(name = "id", description = "用户主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteUser(id);
        return Result.success();
    }

}
