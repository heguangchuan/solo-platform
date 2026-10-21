package com.solo.system.service;

import com.solo.system.request.PermissionRequest;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysPermission;

import java.util.List;

public interface SysPermissionService extends IService<SysPermission> {

    List<SysPermission> getTree();

    void createPermission(PermissionRequest request);

    void updatePermission(Long id, PermissionRequest request);

    void deletePermission(Long id);

    void deleteBatch(List<Long> ids);

    List<SysPermission> buildTree(List<SysPermission> allPermissions, Long parentId);
}
