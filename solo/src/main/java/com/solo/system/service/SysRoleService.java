package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysRole;
import com.solo.system.request.RolePageRequest;
import com.solo.system.request.RoleRequest;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {

    Page<SysRole> pageQuery(RolePageRequest req);

    void createRole(RoleRequest request);

    void updateRole(Long id, RoleRequest request);

    void deleteRole(Long id);

    void deleteBatch(List<Long> ids);

    List<Long> listPermissionIds(Long roleId);

    void assignPermissions(Long roleId, List<Long> permissionIds);
}
