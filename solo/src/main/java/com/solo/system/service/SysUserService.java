package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysUser;
import com.solo.system.request.UserRequest;
import com.solo.system.request.UserPageRequest;
import com.solo.system.request.ProfileUpdateRequest;
import com.solo.system.response.LoginResponse;
import com.solo.system.response.RefreshTokenResponse;
import com.solo.system.response.UserAuthResponse;
import com.solo.system.response.UserInfoVO;
import com.solo.system.response.UserStatusCountVO;

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
     * 用户登录认证（认证失败抛出携带 401/403 状态码的 ServiceException）
     */
    LoginResponse login(String username, String password);

    /**
     * 用 refresh token 换发新的 access / refresh token（滚动刷新）；失效时抛出 401 ServiceException
     */
    RefreshTokenResponse refresh(String refreshToken);

    /**
     * 分页查询用户列表
     */
    Page<SysUser> pageQuery(UserPageRequest req);

    /**
     * 按状态统计用户数（全局口径，与查询条件无关）
     */
    List<UserStatusCountVO> statusCount();

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
