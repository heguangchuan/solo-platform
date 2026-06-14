package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.entity.SysRole;
import com.solo.entity.SysUser;
import com.solo.request.UserRequest;
import com.solo.request.UserPageRequest;
import com.solo.response.UserAuthResponse;

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
}
