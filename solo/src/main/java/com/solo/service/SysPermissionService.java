package com.solo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.common.ServiceException;
import com.solo.entity.SysPermission;

import java.util.List;

/**
 * 系统权限服务接口
 * 提供权限查询、树形结构构建及权限CRUD相关业务逻辑
 */
public interface SysPermissionService extends IService<SysPermission> {

    /**
     * 获取权限树形结构列表
     *
     * @return 按父级ID和排序号排列的权限列表
     */
    List<SysPermission> getTree();

    /**
     * 新增权限
     * 校验权限编码唯一性
     *
     * @param permissionName 权限名称
     * @param permissionCode 权限编码
     * @param url            关联URL
     * @param type           类型
     * @param parentId       父级ID
     * @param sort           排序
     * @param icon           图标
     * @param status         状态
     */
    void createPermission(String permissionName, String permissionCode, String url, Integer type,
                          Long parentId, Integer sort, String icon, Integer status);

    /**
     * 修改权限信息
     *
     * @param id             权限ID
     * @param permissionName 权限名称
     * @param permissionCode 权限编码
     * @param url            关联URL
     * @param type           类型
     * @param parentId       父级ID
     * @param sort           排序
     * @param icon           图标
     * @param status         状态
     */
    void updatePermission(Long id, String permissionName, String permissionCode, String url,
                          Integer type, Long parentId, Integer sort, String icon, Integer status);

    /**
     * 删除权限
     *
     * @param id 权限ID
     */
    void deletePermission(Long id);

    /**
     * 递归构建树形结构
     *
     * @param allPermissions 完整权限列表
     * @param parentId       父级ID
     * @return 筛选后的树形权限列表
     */
    List<SysPermission> buildTree(List<SysPermission> allPermissions, Long parentId);
}
