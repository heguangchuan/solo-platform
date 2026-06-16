package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.common.SystemConstants;
import com.solo.entity.SysRole;
import com.solo.entity.SysRolePermission;
import com.solo.entity.SysUserRole;
import com.solo.mapper.SysRoleMapper;
import com.solo.mapper.SysRolePermissionMapper;
import com.solo.mapper.SysUserRoleMapper;
import com.solo.request.RolePageRequest;
import com.solo.request.RoleRequest;
import com.solo.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统角色服务实现类
 * 封装角色相关的业务逻辑，包括CRUD及关联关系清理
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;

    @Override
    public Page<SysRole> pageQuery(RolePageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<SysRole> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (req.getRoleCode() != null && !req.getRoleCode().isBlank()) {
            wrapper.like(SysRole::getRoleCode, req.getRoleCode());
        }
        if (req.getRoleName() != null && !req.getRoleName().isBlank()) {
            wrapper.like(SysRole::getRoleName, req.getRoleName());
        }
        wrapper.orderByDesc(SysRole::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void createRole(RoleRequest request) {
        SysRole existing = getOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, request.getRoleCode()));
        if (existing != null) {
            throw new ServiceException("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus() != null ? request.getStatus() : SystemConstants.STATUS_ENABLED);
        save(role);
    }

    @Override
    public void updateRole(Long id, RoleRequest request) {
        SysRole role = getById(id);
        if (role == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "角色不存在");
        }
        if (request.getRoleName() != null) role.setRoleName(request.getRoleName());
        if (request.getDescription() != null) role.setDescription(request.getDescription());
        if (request.getStatus() != null) role.setStatus(request.getStatus());
        updateById(role);
    }

    @Override
    public void deleteRole(Long id) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        removeById(id);
    }

    @Override
    public void deleteBatch(java.util.List<Long> ids) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, ids));
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getRoleId, ids));
        removeByIds(ids);
    }

    @Override
    public List<Long> listPermissionIds(Long roleId) {
        if (roleId == null) return new ArrayList<>();
        List<SysRolePermission> list = sysRolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        return list.stream().map(SysRolePermission::getPermissionId).toList();
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (roleId == null) {
            throw new ServiceException("角色ID不能为空");
        }
        SysRole role = getById(roleId);
        if (role == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "角色不存在");
        }
        sysRolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        if (permissionIds == null || permissionIds.isEmpty()) return;
        for (Long permissionId : permissionIds) {
            if (permissionId == null) continue;
            SysRolePermission entry = new SysRolePermission();
            entry.setRoleId(roleId);
            entry.setPermissionId(permissionId);
            sysRolePermissionMapper.insert(entry);
        }
    }
}
