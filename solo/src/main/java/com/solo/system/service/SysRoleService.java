package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysRole;
import com.solo.system.request.RolePageRequest;
import com.solo.system.request.RoleRequest;

import java.util.List;

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

    /**
     * 批量删除角色及关联关系
     */
    void deleteBatch(List<Long> ids);

    /**
     * 查询角色已分配的权限ID列表
     */
    List<Long> listPermissionIds(Long roleId);

    /**
     * 给角色分配权限（覆盖式：先全清，再写入）
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);
}
