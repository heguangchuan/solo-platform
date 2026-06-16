package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.common.SystemConstants;
import com.solo.entity.SysUser;
import com.solo.entity.SysUserRole;
import com.solo.mapper.SysUserMapper;
import com.solo.mapper.SysUserRoleMapper;
import com.solo.request.ProfileUpdateRequest;
import com.solo.request.UserPageRequest;
import com.solo.request.UserRequest;
import com.solo.response.LoginResponse;
import com.solo.response.UserAuthResponse;
import com.solo.response.UserInfoVO;
import com.solo.service.SysUserService;
import com.solo.utils.JwtUtils;
import com.solo.utils.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统用户服务实现类
 * 封装用户认证、信息查询、菜单构建及用户管理
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final JwtUtils jwtUtils;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    public SysUser getByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    @Override
    public List<String> getPermissionCodes(Long userId) {
        return getBaseMapper().selectPermissionCodes(userId);
    }

    @Override
    public Result<LoginResponse> login(String username, String password) {
        SysUser user = getByUsername(username);
        if (user == null) {
            return Result.error(SystemConstants.HTTP_UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == SystemConstants.STATUS_DISABLED) {
            return Result.error(SystemConstants.HTTP_UNAUTHORIZED, "账户已被禁用");
        }
        if (!PasswordUtils.md5(password).equals(user.getPassword())) {
            return Result.error(SystemConstants.HTTP_UNAUTHORIZED, "用户名或密码错误");
        }
        LoginResponse loginResponse = new LoginResponse();
        String token = jwtUtils.generateToken(user.getUsername());
        loginResponse.setToken(token);
        loginResponse.setUser(buildUserInfoVO(user));
        // 将Token存入Redis
        jwtUtils.saveToken(user.getUsername(), token);
        return Result.success(loginResponse);
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
    }

    @Override
    public void switchStatus(Long id, Integer status) {
        SysUser user = getById(id);
        if (user == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "用户不存在");
        }
        user.setStatus(status);
        updateById(user);
    }

    @Override
    public void deleteUser(Long id) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        removeById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, ids));
        removeByIds(ids);
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        if (userId == null) return new ArrayList<>();
        List<SysUserRole> list = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        return list.stream().map(SysUserRole::getRoleId).toList();
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "用户不存在");
        }
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) return;
        for (Long roleId : roleIds) {
            if (roleId == null) continue;
            SysUserRole entry = new SysUserRole();
            entry.setUserId(userId);
            entry.setRoleId(roleId);
            sysUserRoleMapper.insert(entry);
        }
    }

    @Override
    public List<UserAuthResponse> auth(Long userId) {
        return getBaseMapper().auth(userId);
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
