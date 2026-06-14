package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.SysRole;
import com.solo.request.RolePageRequest;

/**
 * 系统角色服务接口
 * 提供角色分页查询及角色CRUD相关业务逻辑
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 分页查询角色列表
     */
    Page<SysRole> pageQuery(RolePageRequest req);

    /**
     * 新增角色
     */
    void createRole(RoleRequest request);

    /**
     * 修改角色信息
     */
    void updateRole(Long id, RoleRequest request);

    /**
     * 删除角色及关联关系
     */
    void deleteRole(Long id);
}
