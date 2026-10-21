package com.solo.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.aop.PermissionCache;
import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;
import com.solo.system.entity.SysRole;
import com.solo.system.entity.SysRolePermission;
import com.solo.system.entity.SysUserRole;
import com.solo.system.mapper.SysRoleMapper;
import com.solo.system.mapper.SysRolePermissionMapper;
import com.solo.system.mapper.SysUserRoleMapper;
import com.solo.system.request.RolePageRequest;
import com.solo.system.request.RoleRequest;
import com.solo.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final PermissionCache permissionCache;

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
        Integer oldStatus = role.getStatus();
        if (request.getRoleName() != null) role.setRoleName(request.getRoleName());
        if (request.getDescription() != null) role.setDescription(request.getDescription());
        if (request.getStatus() != null) role.setStatus(request.getStatus());
        updateById(role);
        // 启用/禁用切换会影响该角色下所有用户的权限计算，需要失效
        if (request.getStatus() != null && !request.getStatus().equals(oldStatus)) {
            permissionCache.evictAll();
        }
    }

    @Override
    public void deleteRole(Long id) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        removeById(id);
        // 该角色可能挂在若干用户身上；单个失效成本太高，直接全清。
        permissionCache.evictAll();
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
        if (permissionIds == null || permissionIds.isEmpty()) {
            // 角色权限被清空，仍需失效缓存
            permissionCache.evictAll();
            return;
        }
        for (Long permissionId : permissionIds) {
            if (permissionId == null) continue;
            SysRolePermission entry = new SysRolePermission();
            entry.setRoleId(roleId);
            entry.setPermissionId(permissionId);
            sysRolePermissionMapper.insert(entry);
        }
        // 该角色下所有用户的权限都可能变了，直接全清（简单可靠）
        permissionCache.evictAll();
    }
}
