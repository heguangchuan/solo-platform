package com.solo.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.common.utils.JwtUtils;
import com.solo.common.utils.PasswordUtils;
import com.solo.common.utils.UserHolderUtils;
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
        if (username == null || username.isBlank() || password == null) {
            throw unauthorized();
        }
        SysUser user = getByUsername(username.trim());
        // 不存在、禁用、密码错误统一口径，避免枚举账号状态
        if (user == null || !Integer.valueOf(SystemConstants.STATUS_ENABLED).equals(user.getStatus())
                || !PasswordUtils.matches(password, user.getPassword())) {
            throw unauthorized();
        }
        upgradeLegacyPassword(user, password);

        LoginResponse loginResponse = new LoginResponse();
        String token = jwtUtils.generateToken(user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getUsername());
        loginResponse.setToken(token);
        loginResponse.setRefreshToken(refreshToken);
        loginResponse.setUser(buildUserInfoVO(user));
        jwtUtils.saveToken(user.getUsername(), token);
        jwtUtils.saveRefreshToken(user.getUsername(), refreshToken);
        return loginResponse;
    }

    private void upgradeLegacyPassword(SysUser user, String rawPassword) {
        if (!PasswordUtils.isLegacyMd5(user.getPassword())) {
            return;
        }
        // 先判断旧默认密码再换哈希，否则随机 salt 会让旧检测逻辑失效
        if (PasswordUtils.isDefaultPassword(user.getUsername(), user.getPassword())) {
            user.setPwdResetRequired(SystemConstants.PWD_RESET_REQUIRED);
        }
        user.setPassword(PasswordUtils.encode(rawPassword));
        updateById(user);
    }

    @Override
    public RefreshTokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "登录已过期，请重新登录");
        }
        String username;
        try {
            username = jwtUtils.getUsernameFromToken(refreshToken);
        } catch (Exception e) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "登录已过期，请重新登录");
        }
        if (!jwtUtils.validateRefreshToken(refreshToken, username)) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "登录已过期，请重新登录");
        }
        SysUser user = getByUsername(username);
        if (user == null || !Integer.valueOf(SystemConstants.STATUS_ENABLED).equals(user.getStatus())) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "登录已过期，请重新登录");
        }
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
        if (page < 1 || pageSize < 1 || pageSize > 20_000L) {
            throw new ServiceException("分页参数不合法");
        }
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
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        if (username.isEmpty() || username.length() > 50) {
            throw new ServiceException("用户名不能为空且长度不能超过 50 位");
        }
        PasswordUtils.requireStrong(request.getInitialPassword());
        SysUser existing = getByUsername(username);
        if (existing != null) {
            throw new ServiceException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(PasswordUtils.encode(request.getInitialPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAvatar(request.getAvatar());
        user.setStatus(request.getStatus() != null ? request.getStatus() : SystemConstants.STATUS_ENABLED);
        user.setPwdResetRequired(SystemConstants.PWD_RESET_REQUIRED);
        save(user);
    }

    @Override
    @Transactional
    public void updateUser(Long id, UserRequest request) {
        SysUser user = requireUser(id);
        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getAvatar() != null) user.setAvatar(request.getAvatar());
        if (request.getStatus() != null) {
            requireValidStatus(request.getStatus());
            if (request.getStatus() == SystemConstants.STATUS_DISABLED) {
                ensureCanDisableOrDelete(user);
            }
            user.setStatus(request.getStatus());
        }
        updateById(user);
        if (Integer.valueOf(SystemConstants.STATUS_DISABLED).equals(request.getStatus())) {
            jwtUtils.removeToken(user.getUsername());
        }
    }

    @Override
    @Transactional
    public void switchStatus(Long id, Integer status) {
        requireValidStatus(status);
        SysUser user = requireUser(id);
        if (status == SystemConstants.STATUS_DISABLED) {
            ensureCanDisableOrDelete(user);
        }
        user.setStatus(status);
        updateById(user);
        if (status == SystemConstants.STATUS_DISABLED) {
            jwtUtils.removeToken(user.getUsername());
        }
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        SysUser user = requireUser(id);
        ensureCanDisableOrDelete(user);
        removeById(id);
        jwtUtils.removeToken(user.getUsername());
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if (ids.size() > 1000) {
            throw new ServiceException("单次最多删除 1000 个用户");
        }
        List<Long> distinctIds = ids.stream().distinct().toList();
        Long currentUserId = currentUserId();
        if (currentUserId != null && distinctIds.contains(currentUserId)) {
            throw new ServiceException("不能删除当前登录账号");
        }
        List<SysUser> users = listByIds(distinctIds);
        if (users.isEmpty()) {
            return;
        }
        ensureRemainingEnabledUser(distinctIds);
        removeByIds(distinctIds);
        users.forEach(user -> jwtUtils.removeToken(user.getUsername()));
    }

    @Override
    public UserInfoVO getProfile(Long userId) {
        if (userId == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "未登录");
        }
        return buildUserInfoVO(requireUser(userId));
    }

    @Override
    public void updateProfile(Long userId, ProfileUpdateRequest request) {
        if (userId == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "未登录");
        }
        SysUser user = requireUser(userId);
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
        SysUser user = requireUser(userId);
        if (!PasswordUtils.matches(oldPassword, user.getPassword())) {
            throw new ServiceException("原密码不正确");
        }
        if (newPassword != null && newPassword.equals(oldPassword)) {
            throw new ServiceException("新密码不能与原密码相同");
        }
        PasswordUtils.requireStrong(newPassword);
        user.setPassword(PasswordUtils.encode(newPassword));
        user.setPwdResetRequired(SystemConstants.PWD_RESET_NOT_REQUIRED);
        updateById(user);
        // 改密后旧 access/refresh 都不能再继续使用
        jwtUtils.removeToken(user.getUsername());
    }

    @Override
    public boolean isPasswordResetRequired(SysUser user) {
        return Integer.valueOf(SystemConstants.PWD_RESET_REQUIRED).equals(user.getPwdResetRequired())
                || PasswordUtils.isDefaultPassword(user.getUsername(), user.getPassword());
    }

    private SysUser requireUser(Long id) {
        SysUser user = id == null ? null : getById(id);
        if (user == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void ensureCanDisableOrDelete(SysUser user) {
        Long currentUserId = currentUserId();
        if (currentUserId != null && currentUserId.equals(user.getId())) {
            throw new ServiceException("不能禁用或删除当前登录账号");
        }
        if (Integer.valueOf(SystemConstants.STATUS_ENABLED).equals(user.getStatus())) {
            ensureRemainingEnabledUser(List.of(user.getId()));
        }
    }

    private void ensureRemainingEnabledUser(List<Long> excludedIds) {
        Long remaining = lambdaQuery()
                .eq(SysUser::getStatus, SystemConstants.STATUS_ENABLED)
                .notIn(SysUser::getId, excludedIds)
                .count();
        if (remaining == 0) {
            throw new ServiceException("至少需要保留一个可用账号");
        }
    }

    private Long currentUserId() {
        try {
            return UserHolderUtils.getCurrentUserId();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static void requireValidStatus(Integer status) {
        if (status == null || (status != SystemConstants.STATUS_DISABLED
                && status != SystemConstants.STATUS_ENABLED)) {
            throw new ServiceException("用户状态只能是 0 或 1");
        }
    }

    private static ServiceException unauthorized() {
        return new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "用户名或密码错误");
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
        vo.setPwdResetRequired(isPasswordResetRequired(user)
                ? SystemConstants.PWD_RESET_REQUIRED : SystemConstants.PWD_RESET_NOT_REQUIRED);
        return vo;
    }
}
