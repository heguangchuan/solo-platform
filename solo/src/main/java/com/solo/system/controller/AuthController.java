package com.solo.system.controller;

import com.solo.aop.OperationLog;
import com.solo.aop.RateLimiter;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.Result;
import com.solo.system.request.ChangePasswordRequest;
import com.solo.system.request.LoginRequest;
import com.solo.system.request.ProfileUpdateRequest;
import com.solo.system.request.RefreshTokenRequest;
import com.solo.system.response.LoginResponse;
import com.solo.system.response.RefreshTokenResponse;
import com.solo.system.response.StatResponse;
import com.solo.system.response.UserAuthResponse;
import com.solo.system.response.UserInfoVO;
import com.solo.system.entity.SysRole;
import com.solo.system.service.SysPermissionService;
import com.solo.system.service.SysRoleService;
import com.solo.system.service.SysUserService;
import com.solo.common.utils.TreeUtils;
import com.solo.common.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
    @OperationLog(module = "登录认证", type = BusinessType.LOGIN, description = "用户登录", recordParam = false)
    @RateLimiter(maxCount = 5, time = 1, timeUnit = TimeUnit.MINUTES, message = "登录次数过多，请1分钟后再试")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        return sysUserService.login(loginRequest.getUsername(), loginRequest.getPassword());
    }

    /**
     * 刷新 Token 接口
     * 使用 refresh token 换取新的 access / refresh token（滚动刷新，旧 refresh 自动失效）
     * 不走 JwtInterceptor —— refresh 时 access token 通常已过期
     */
    @PostMapping("/refresh")
    @RateLimiter(maxCount = 10, time = 1, timeUnit = TimeUnit.MINUTES, message = "刷新过于频繁，请稍后再试")
    public Result<RefreshTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return sysUserService.refresh(request.getRefreshToken());
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
                UserAuthResponse::setChildren
        ));
    }

    /**
     * 获取当前用户权限编码列表
     */
    @GetMapping("/permissions")
    public Result<List<String>> permissions() {
        return Result.success(sysUserService.getPermissionCodes(UserHolderUtils.getCurrentUserId()));
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

    /**
     * 修改当前登录用户的密码（首次登录强制改密也走该接口）
     */
    @PostMapping("/password")
    @OperationLog(module = "登录认证", type = BusinessType.EDIT, description = "修改密码", recordParam = false)
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        sysUserService.changePassword(
                UserHolderUtils.getCurrentUserId(),
                request.getOldPassword(),
                request.getNewPassword());
        return Result.success();
    }

    /**
     * 获取当前登录用户档案（含是否需要强制改密的标记）
     */
    @GetMapping("/profile")
    public Result<UserInfoVO> profile() {
        return Result.success(sysUserService.getProfile(UserHolderUtils.getCurrentUserId()));
    }

    /**
     * 当前登录用户更新自身资料（仅可修改昵称/邮箱/手机号/头像）
     */
    @PutMapping("/profile")
    @OperationLog(module = "个人中心", type = BusinessType.EDIT, description = "更新个人资料")
    public Result<Void> updateProfile(@RequestBody ProfileUpdateRequest request) {
        sysUserService.updateProfile(UserHolderUtils.getCurrentUserId(), request);
        return Result.success();
    }

    /**
     * 获取当前登录用户的角色列表（个人中心展示用）
     */
    @GetMapping("/roles")
    public Result<List<SysRole>> myRoles() {
        Long userId = UserHolderUtils.getCurrentUserId();
        List<Long> roleIds = sysUserService.listRoleIds(userId);
        if (roleIds == null || roleIds.isEmpty()) return Result.success(List.of());
        return Result.success(sysRoleService.listByIds(roleIds));
    }
}
