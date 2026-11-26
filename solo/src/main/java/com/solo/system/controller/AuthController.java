package com.solo.system.controller;

import com.solo.aop.RateLimiter;
import com.solo.common.Result;
import com.solo.common.utils.UserHolderUtils;
import com.solo.system.request.ChangePasswordRequest;
import com.solo.system.request.LoginRequest;
import com.solo.system.request.ProfileUpdateRequest;
import com.solo.system.request.RefreshTokenRequest;
import com.solo.system.response.LoginResponse;
import com.solo.system.response.RefreshTokenResponse;
import com.solo.system.response.UserInfoVO;
import com.solo.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@Tag(name = "认证与当前用户管理", description = "认证与当前用户管理")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    @RateLimiter(maxCount = 5, time = 1, timeUnit = TimeUnit.MINUTES, message = "登录次数过多，请1分钟后再试")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        return Result.success(sysUserService.login(loginRequest.getUsername(), loginRequest.getPassword()));
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    @RateLimiter(maxCount = 10, time = 1, timeUnit = TimeUnit.MINUTES, message = "刷新过于频繁，请稍后再试")
    public Result<RefreshTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return Result.success(sysUserService.refresh(request.getRefreshToken()));
    }

    @Operation(summary = "修改当前登录用户的密码")
    @PostMapping("/password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        sysUserService.changePassword(
                UserHolderUtils.getCurrentUserId(),
                request.getOldPassword(),
                request.getNewPassword());
        return Result.success();
    }

    @Operation(summary = "获取当前登录用户档案")
    @GetMapping("/profile")
    public Result<UserInfoVO> profile() {
        return Result.success(sysUserService.getProfile(UserHolderUtils.getCurrentUserId()));
    }

    @Operation(summary = "当前登录用户更新自身资料")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody ProfileUpdateRequest request) {
        sysUserService.updateProfile(UserHolderUtils.getCurrentUserId(), request);
        return Result.success();
    }
}
