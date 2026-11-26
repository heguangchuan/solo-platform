package com.solo.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.common.utils.JwtUtils;
import com.solo.common.utils.PasswordUtils;
import com.solo.system.constant.SystemConstants;
import com.solo.system.entity.SysUser;
import com.solo.system.mapper.SysUserMapper;
import com.solo.system.request.ProfileUpdateRequest;
import com.solo.system.request.UserPageRequest;
import com.solo.system.request.UserRequest;
import com.solo.system.response.LoginResponse;
import com.solo.system.response.RefreshTokenResponse;
import com.solo.system.response.UserInfoVO;
import com.solo.system.response.UserStatusCountVO;
import com.solo.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final JwtUtils jwtUtils;

    @Override
    public SysUser getByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    @Override
    public LoginResponse login(String username, String password) {
        SysUser user = getByUsername(username);
        if (user == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == SystemConstants.STATUS_DISABLED) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "账户已被禁用");
        }
        if (!PasswordUtils.md5(password).equals(user.getPassword())) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "用户名或密码错误");
        }
        LoginResponse loginResponse = new LoginResponse();
        String token = jwtUtils.generateToken(user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getUsername());
        loginResponse.setToken(token);
        loginResponse.setRefreshToken(refreshToken);
        loginResponse.setUser(buildUserInfoVO(user));
        // 当前 access token 存入 Redis，JWT 拦截器会逐请求比对，重新登录后旧 token 立即失效
        jwtUtils.saveToken(user.getUsername(), token);
        // 单独保存 refresh token，TTL 与 refresh 有效期一致，每次刷新会被覆盖（滚动失效）
        jwtUtils.saveRefreshToken(user.getUsername(), refreshToken);
        return loginResponse;
    }

    @Override
    public RefreshTokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "登录已过期，请重新登录");
        }
        // 解析用户名（签名/格式异常 → 401）
        String username;
        try {
            username = jwtUtils.getUsernameFromToken(refreshToken);
        } catch (Exception e) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "登录已过期，请重新登录");
        }
        // 校验签名 + 过期 + type=refresh + Redis 一致性
        if (!jwtUtils.validateRefreshToken(refreshToken, username)) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "登录已过期，请重新登录");
        }
        // 用户仍存在且未被禁用
        SysUser user = getByUsername(username);
        if (user == null || user.getStatus() == SystemConstants.STATUS_DISABLED) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "登录已过期，请重新登录");
        }
        // 滚动：换发一对全新的 access / refresh token，旧 refresh 因 Redis 被覆盖而失效
        String newAccess = jwtUtils.generateToken(username);
        String newRefresh = jwtUtils.generateRefreshToken(username);
        jwtUtils.saveToken(username, newAccess);
        jwtUtils.saveRefreshToken(username, newRefresh);
        RefreshTokenResponse resp = new RefreshTokenResponse();
        resp.setToken(newAccess);
        resp.setRefreshToken(newRefresh);
        return resp;
    }

    @Override
    public Page<SysUser> pageQuery(UserPageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 10L;
        Page<SysUser> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            wrapper.like(SysUser::getUsername, req.getUsername());
        }
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            wrapper.like(SysUser::getNickname, req.getNickname());
        }
        if (req.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, req.getStatus());
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public List<UserStatusCountVO> statusCount() {
        return getBaseMapper().selectStatusCount();
    }

    @Override
    @Transactional
    public void createUser(UserRequest request) {
        SysUser existing = getByUsername(request.getUsername());
        if (existing != null) {
            throw new ServiceException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(PasswordUtils.defaultPasswordHashOf(request.getUsername()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAvatar(request.getAvatar());
        user.setStatus(request.getStatus() != null ? request.getStatus() : SystemConstants.STATUS_ENABLED);
        user.setPwdResetRequired(SystemConstants.PWD_RESET_REQUIRED);
        save(user);
    }

    @Override
    public void updateUser(Long id, UserRequest request) {
        SysUser user = getById(id);
        if (user == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "用户不存在");
        }
        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getAvatar() != null) user.setAvatar(request.getAvatar());
        if (request.getStatus() != null) user.setStatus(request.getStatus());
        updateById(user);
        if (request.getStatus() != null && request.getStatus() == SystemConstants.STATUS_DISABLED) {
            jwtUtils.removeToken(user.getUsername());
        }
    }

    @Override
    public void switchStatus(Long id, Integer status) {
        SysUser user = getById(id);
        if (user == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "用户不存在");
        }
        user.setStatus(status);
        updateById(user);
        if (status != null && status == SystemConstants.STATUS_DISABLED) {
            jwtUtils.removeToken(user.getUsername());
        }
    }

    @Override
    public void deleteUser(Long id) {
        SysUser user = getById(id);
        removeById(id);
        if (user != null) {
            jwtUtils.removeToken(user.getUsername());
        }
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<SysUser> users = listByIds(ids);
        removeByIds(ids);
        users.forEach(user -> jwtUtils.removeToken(user.getUsername()));
    }

    @Override
    public UserInfoVO getProfile(Long userId) {
        if (userId == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "未登录");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "用户不存在");
        }
        return buildUserInfoVO(user);
    }

    @Override
    public void updateProfile(Long userId, ProfileUpdateRequest request) {
        if (userId == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "未登录");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "用户不存在");
        }
        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getAvatar() != null) user.setAvatar(request.getAvatar());
        updateById(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "未登录");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "用户不存在");
        }
        if (oldPassword == null || !PasswordUtils.md5(oldPassword).equals(user.getPassword())) {
            throw new ServiceException("原密码不正确");
        }
        if (newPassword != null && newPassword.equals(oldPassword)) {
            throw new ServiceException("新密码不能与原密码相同");
        }
        PasswordUtils.requireStrong(newPassword);
        user.setPassword(PasswordUtils.md5(newPassword));
        user.setPwdResetRequired(SystemConstants.PWD_RESET_NOT_REQUIRED);
        updateById(user);
    }

    private UserInfoVO buildUserInfoVO(SysUser user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        Integer flag = user.getPwdResetRequired();
        // 兜底：若 DB 标记为 0 但密码仍是默认密码，仍视为需要强制改密
        boolean required = flag != null && flag == SystemConstants.PWD_RESET_REQUIRED
                || PasswordUtils.isDefaultPassword(user.getUsername(), user.getPassword());
        vo.setPwdResetRequired(required ? SystemConstants.PWD_RESET_REQUIRED : SystemConstants.PWD_RESET_NOT_REQUIRED);
        return vo;
    }
}
