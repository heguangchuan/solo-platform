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
 * 认证与当前用户接口
 *
 * <p>面向登录用户的认证 / 个人中心相关入口：登录、Token 刷新、当前用户信息、统计、强制改密等。
 * 不含用户 / 角色 / 权限的 CRUD——见 {@link UserController} / {@link RoleController} / {@link PermissionController}。</p>
 *
 * <h3>Token 机制</h3>
 * <ul>
 *   <li>登录成功返回双 Token：{@code token}（access，30 分钟）、{@code refreshToken}（refresh，7 天）</li>
 *   <li>前端请求除登录/刷新/按类型取字典等公开接口外，全部需在 {@code Authorization: Bearer <token>} 头里带 access token</li>
 *   <li>access 过期后用 refresh 调 {@code POST /refresh} 换发新 access（同时 refresh 也会换新，旧 refresh 立即失效）</li>
 *   <li>refresh 也过期就只能重新走登录流程</li>
 * </ul>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/auth/login} — 账号密码登录，返回 access/refresh token</li>
 *   <li>{@code POST /api/auth/refresh} — 用 refresh token 换发新 token 对（旧 refresh 自动失效）</li>
 *   <li>{@code GET  /api/auth/me} — 取当前用户菜单树（{@code type=1}），供前端动态路由</li>
 *   <li>{@code GET  /api/auth/permissions} — 取当前用户权限码（permissionCode）列表</li>
 *   <li>{@code GET  /api/auth/stats} — 系统统计（用户/角色/权限总数）</li>
 *   <li>{@code POST /api/auth/password} — 当前用户改密（含首次登录强制改密）</li>
 *   <li>{@code GET  /api/auth/profile} — 取当前用户档案</li>
 *   <li>{@code PUT  /api/auth/profile} — 当前用户更新自身资料（昵称/邮箱/手机/头像）</li>
 *   <li>{@code GET  /api/auth/roles} — 取当前用户已分配角色（个人中心展示）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final SysPermissionService sysPermissionService;

    /**
     * 用户登录
     *
     * <p>校验用户名密码，成功后签发 access + refresh Token。限流 5 次/分钟，触发后返回 4xx。</p>
     *
     * <p>失败常见返回：401（账号或密码错误）、429（限流）。</p>
     */
    @PostMapping("/login")
    @OperationLog(module = "登录认证", type = BusinessType.LOGIN, description = "用户登录", recordParam = false)
    @RateLimiter(maxCount = 5, time = 1, timeUnit = TimeUnit.MINUTES, message = "登录次数过多，请1分钟后再试")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        return sysUserService.login(loginRequest.getUsername(), loginRequest.getPassword());
    }

    /**
     * 刷新 Token
     *
     * <p>用 refresh token 换发新的 access/refresh 对；旧 refresh token 同步失效。
     * 限流 10 次/分钟。refresh 过期或被吊销时返回 401。</p>
     */
    @PostMapping("/refresh")
    @RateLimiter(maxCount = 10, time = 1, timeUnit = TimeUnit.MINUTES, message = "刷新过于频繁，请稍后再试")
    public Result<RefreshTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return sysUserService.refresh(request.getRefreshToken());
    }

    /**
     * 获取当前用户菜单树
     *
     * <p>仅菜单（{@code type=1}），按 {@code parentId=0} 兜底建树，直接返回渲染所需字段。
     * 字段语义见 {@link UserAuthResponse}；前端可直接按 children 递归渲染菜单。</p>
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
     * 获取当前用户权限编码列表（按钮级控制码）
     *
     * <p>形如 {@code "system:user:list"} 的字符串数组，对应前端按钮的 {@code v-permission} 指令值。
     * 没权限的按钮在前端通过该指令控制显隐。</p>
     */
    @GetMapping("/permissions")
    public Result<List<String>> permissions() {
        return Result.success(sysUserService.getPermissionCodes(UserHolderUtils.getCurrentUserId()));
    }

    /**
     * 获取系统统计信息
     *
     * <p>返回用户 / 角色 / 权限三类资源总数，常用于首页卡片展示。
     * 需同时具备 {@code system:user:list} / {@code system:role:list} / {@code system:permission:list} 三个权限码，
     * 否则返回 403。</p>
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
     * 修改当前登录用户的密码
     *
     * <p>首次登录强制改密也走该接口：服务端判断 {@code pwdResetRequired=1} 时同样会拦在登录页。
     * 失败常见返回：400（旧密码错误 / 新密码与旧密码相同 / 新密码不满足强密码规则）。</p>
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
     * 获取当前登录用户档案
     *
     * <p>含是否首次登录强制改密标记（{@code pwdResetRequired}，{@code 0=否}、{@code 1=是}）。
     * 前端登录后据此决定是否弹"修改默认密码"提示框。</p>
     */
    @GetMapping("/profile")
    public Result<UserInfoVO> profile() {
        return Result.success(sysUserService.getProfile(UserHolderUtils.getCurrentUserId()));
    }

    /**
     * 当前登录用户更新自身资料
     *
     * <p>仅可修改昵称 / 邮箱 / 手机号 / 头像；账号 / 状态等字段不可变更。
     * 邮箱、手机号建议做基础格式校验后再提交。</p>
     */
    @PutMapping("/profile")
    @OperationLog(module = "个人中心", type = BusinessType.EDIT, description = "更新个人资料")
    public Result<Void> updateProfile(@RequestBody ProfileUpdateRequest request) {
        sysUserService.updateProfile(UserHolderUtils.getCurrentUserId(), request);
        return Result.success();
    }

    /**
     * 获取当前登录用户的角色列表（个人中心展示用）
     *
     * <p>返回当前用户已分配的角色全量数据；未分配任何角色时返回空数组。</p>
     */
    @GetMapping("/roles")
    public Result<List<SysRole>> myRoles() {
        Long userId = UserHolderUtils.getCurrentUserId();
        List<Long> roleIds = sysUserService.listRoleIds(userId);
        if (roleIds == null || roleIds.isEmpty()) return Result.success(List.of());
        return Result.success(sysRoleService.listByIds(roleIds));
    }
}
