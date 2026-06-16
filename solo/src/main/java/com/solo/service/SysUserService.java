package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.entity.SysRole;
import com.solo.entity.SysUser;
import com.solo.request.UserRequest;
import com.solo.request.UserPageRequest;
import com.solo.request.ProfileUpdateRequest;
import com.solo.response.UserAuthResponse;
import com.solo.response.UserInfoVO;

import java.util.List;

/**
 * 系统用户服务接口
 * 提供用户认证、信息查询及用户管理相关业务逻辑
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 根据用户名查询用户
     */
    SysUser getByUsername(String username);

    /**
     * 查询用户可见的权限列表
     */
    List<UserAuthResponse> auth(Long userId);

    /**
     * 获取用户拥有的权限编码列表
     */
    List<String> getPermissionCodes(Long userId);

    /**
     * 用户登录认证
     */
    Result<com.solo.response.LoginResponse> login(String username, String password);

    /**
     * 分页查询用户列表
     */
    Page<SysUser> pageQuery(UserPageRequest req);

    /**
     * 新增用户
     */
    void createUser(UserRequest request);

    /**
     * 修改用户信息
     */
    void updateUser(Long id, UserRequest request);

    /**
     * 切换用户状态
     */
    void switchStatus(Long id, Integer status);

    /**
     * 删除用户及关联角色关系
     */
    void deleteUser(Long id);

    /**
     * 批量删除用户
     */
    void deleteBatch(List<Long> ids);

    /**
     * 查询用户已分配的角色ID列表
     */
    List<Long> listRoleIds(Long userId);

    /**
     * 给用户分配角色（覆盖式）
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 修改当前登录用户的密码（强密码校验、清除强制改密标记）
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 获取当前登录用户的精简档案（含强制改密标记）
     */
    UserInfoVO getProfile(Long userId);

    /**
     * 当前登录用户更新自身资料（仅可修改昵称/邮箱/手机号/头像）
     */
    void updateProfile(Long userId, ProfileUpdateRequest request);
}
