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

    LoginResponse login(String username, String password);

    RefreshTokenResponse refresh(String refreshToken);

    Page<SysUser> pageQuery(UserPageRequest req);

    List<UserStatusCountVO> statusCount();

    void createUser(UserRequest request);

    void updateUser(Long id, UserRequest request);

    void switchStatus(Long id, Integer status);

    void deleteUser(Long id);

    void deleteBatch(List<Long> ids);

    List<Long> listRoleIds(Long userId);

    void assignRoles(Long userId, List<Long> roleIds);

    void changePassword(Long userId, String oldPassword, String newPassword);

    UserInfoVO getProfile(Long userId);

    void updateProfile(Long userId, ProfileUpdateRequest request);
}
