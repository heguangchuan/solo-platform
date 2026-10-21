package com.solo.system.service;

import com.solo.system.request.PermissionRequest;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysPermission;

import java.util.List;

public interface SysPermissionService extends IService<SysPermission> {

    /** 获取权限树（按父级 ID、排序号排列） */
    List<SysPermission> getTree();

    void createPermission(PermissionRequest request);

    void updatePermission(Long id, PermissionRequest request);

    void deletePermission(Long id);

    void deleteBatch(List<Long> ids);

    /** 从完整权限列表中按 parentId 递归构建树形结构 */
    List<SysPermission> buildTree(List<SysPermission> allPermissions, Long parentId);
}
