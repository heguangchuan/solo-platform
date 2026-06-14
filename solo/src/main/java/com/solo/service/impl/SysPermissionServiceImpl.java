package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.common.SystemConstants;
import com.solo.entity.SysPermission;
import com.solo.mapper.SysPermissionMapper;
import com.solo.service.SysPermissionService;
import com.solo.utils.TreeUtils;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.beans.BeanUtils.copyProperties;

/**
 * 系统权限服务实现类
 * 封装权限相关的业务逻辑，包括树形结构构建和CRUD操作
 */
@Service
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission> implements SysPermissionService {

    @Override
    public List<SysPermission> getTree() {
        return list(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSort));
    }

    @Override
    public void createPermission(String permissionName, String permissionCode, String url,
                                 Integer type, Long parentId, Integer sort, String icon, Integer status) {
        SysPermission existing = getOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, permissionCode));
        if (existing != null) {
            throw new ServiceException("权限编码已存在");
        }
        SysPermission permission = new SysPermission();
        permission.setPermissionName(permissionName);
        permission.setPermissionCode(permissionCode);
        permission.setUrl(url);
        permission.setType(type);
        permission.setParentId(parentId != null ? parentId : SystemConstants.DEFAULT_PARENT_ID);
        permission.setSort(sort != null ? sort : SystemConstants.DEFAULT_SORT);
        permission.setIcon(icon);
        permission.setStatus(status != null ? status : SystemConstants.STATUS_ENABLED);
        save(permission);
    }

    @Override
    public void updatePermission(Long id, String permissionName, String permissionCode, String url,
                                 Integer type, Long parentId, Integer sort, String icon, Integer status) {
        SysPermission permission = getById(id);
        if (permission == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "权限不存在");
        }
        permission.setPermissionName(permissionName);
        permission.setPermissionCode(permissionCode);
        permission.setUrl(url);
        permission.setType(type);
        permission.setParentId(parentId != null ? parentId : SystemConstants.DEFAULT_PARENT_ID);
        permission.setSort(sort != null ? sort : SystemConstants.DEFAULT_SORT);
        permission.setIcon(icon);
        if (status != null) {
            permission.setStatus(status);
        }
        updateById(permission);
    }

    @Override
    public void deletePermission(Long id) {
        removeById(id);
    }

    @Override
    public List<SysPermission> buildTree(List<SysPermission> allPermissions, Long parentId) {
        return TreeUtils.build(
                allPermissions,
                SysPermission::getParentId,
                SysPermission::getId,
                p -> p.getParentId() != null && p.getParentId().equals(parentId),
                (parent, children) -> parent.setChildren(children)
        );
    }
}
