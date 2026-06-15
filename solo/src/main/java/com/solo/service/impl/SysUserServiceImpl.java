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
import com.solo.request.UserPageRequest;
import com.solo.request.UserRequest;
import com.solo.response.LoginResponse;
import com.solo.response.UserAuthResponse;
import com.solo.response.UserInfoVO;
import com.solo.service.SysUserService;
import com.solo.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        if (!md5Encode(password).equals(user.getPassword())) {
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
        user.setPassword(SystemConstants.DEFAULT_PASSWORD);
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAvatar(request.getAvatar());
        user.setStatus(request.getStatus() != null ? request.getStatus() : SystemConstants.STATUS_ENABLED);
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

    private String md5Encode(String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ServiceException("密码加密失败");
        }
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
        return vo;
    }
}
