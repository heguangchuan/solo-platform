package com.solo.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.system.request.PermissionRequest;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;
import com.solo.system.entity.SysPermission;
import com.solo.system.mapper.SysPermissionMapper;
import com.solo.system.service.SysPermissionService;
import com.solo.common.utils.TreeUtils;
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
    public void createPermission(PermissionRequest request) {
        SysPermission existing = getOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, request.getPermissionCode()));
        if (existing != null) {
            throw new ServiceException("权限编码已存在");
        }
        SysPermission permission = new SysPermission();
        permission.setPermissionName(request.getPermissionName());
        permission.setPermissionCode(request.getPermissionCode());
        permission.setUrl(request.getUrl());
        permission.setType(request.getType());
        permission.setParentId(request.getParentId() != null ? request.getParentId() : SystemConstants.DEFAULT_PARENT_ID);
        permission.setSort(request.getSort() != null ? request.getSort() : SystemConstants.DEFAULT_SORT);
        permission.setIcon(request.getIcon());
        permission.setStatus(request.getStatus() != null ? request.getStatus() : SystemConstants.STATUS_ENABLED);
        save(permission);
    }

    @Override
    public void updatePermission(Long id, PermissionRequest request) {
        SysPermission permission = getById(id);
        if (permission == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "权限不存在");
        }
        if (request.getPermissionName() != null) permission.setPermissionName(request.getPermissionName());
        if (request.getPermissionCode() != null) permission.setPermissionCode(request.getPermissionCode());
        if (request.getUrl() != null) permission.setUrl(request.getUrl());
        if (request.getType() != null) permission.setType(request.getType());
        if (request.getParentId() != null) permission.setParentId(request.getParentId());
        if (request.getSort() != null) permission.setSort(request.getSort());
        if (request.getIcon() != null) permission.setIcon(request.getIcon());
        if (request.getStatus() != null) permission.setStatus(request.getStatus());
        updateById(permission);
    }

    @Override
    public void deletePermission(Long id) {
        removeById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        removeByIds(ids);
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
