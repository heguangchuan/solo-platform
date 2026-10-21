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

public interface SysUserService extends IService<SysUser> {

    SysUser getByUsername(String username);

    List<UserAuthResponse> auth(Long userId);

    List<String> getPermissionCodes(Long userId);

    /** 用户登录认证，认证失败抛出携带 401/403 状态码的 ServiceException */
    LoginResponse login(String username, String password);

    /** 用 refresh token 换发新的 access / refresh token（滚动刷新），失效时抛出 401 ServiceException */
    RefreshTokenResponse refresh(String refreshToken);

    Page<SysUser> pageQuery(UserPageRequest req);

    /** 按状态统计用户数（全局口径，与查询条件无关） */
    List<UserStatusCountVO> statusCount();

    void createUser(UserRequest request);

    void updateUser(Long id, UserRequest request);

    void switchStatus(Long id, Integer status);

    void deleteUser(Long id);

    void deleteBatch(List<Long> ids);

    List<Long> listRoleIds(Long userId);

    /** 给用户分配角色（覆盖式） */
    void assignRoles(Long userId, List<Long> roleIds);

    /** 修改当前登录用户的密码（强密码校验、清除强制改密标记） */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** 获取当前登录用户的精简档案（含强制改密标记） */
    UserInfoVO getProfile(Long userId);

    /** 当前登录用户更新自身资料，仅可修改昵称/邮箱/手机号/头像 */
    void updateProfile(Long userId, ProfileUpdateRequest request);
}
