package com.solo.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.request.LoginRequest;
import com.solo.response.LoginResponse;
import com.solo.response.StatResponse;
import com.solo.response.UserAuthResponse;
import com.solo.service.SysPermissionService;
import com.solo.service.SysRoleService;
import com.solo.service.SysUserService;
import com.solo.utils.TreeUtils;
import com.solo.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证控制器
 * 提供用户登录、获取当前用户信息及系统统计功能
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final SysPermissionService sysPermissionService;

    /**
     * 用户登录接口
     * 校验用户名密码，成功后返回JWT Token
     *
     * @param loginRequest 登录请求体
     * @return 包含Token和用户信息的响应
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        return sysUserService.login(loginRequest.getUsername(), loginRequest.getPassword());
    }

    /**
     * 获取当前用户权限树（仅菜单 type=1，直接返回渲染所需字段）
     */
    @GetMapping("/me")
    public Result<List<UserAuthResponse>> me() {
        Long userId = UserHolderUtils.getCurrentUserId();
        List<UserAuthResponse> menus = sysUserService.auth(userId);
        return Result.success(TreeUtils.build(
                menus,
                UserAuthResponse::getParentId,
                UserAuthResponse::getId,
                p -> p.getParentId() != null && p.getParentId().equals(0L),
                (node, children) -> node.setChildren(children)
        ));
    }

    /**
     * 获取系统统计信息
     *
     * @return 包含用户、角色、权限总数的响应
     */
    @GetMapping("/stats")
    @RequirePermission({"system:user:list", "system:role:list", "system:permission:list"})
    public Result<StatResponse> getStats() {
        StatResponse stats = new StatResponse();
        stats.setUserCount(sysUserService.count());
        stats.setRoleCount(sysRoleService.count());
        stats.setPermissionCount(sysPermissionService.count());
        return Result.success(stats);
    }
}
