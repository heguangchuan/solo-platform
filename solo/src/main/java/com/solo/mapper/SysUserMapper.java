package com.solo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.response.UserAuthResponse;
import com.solo.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT r.role_code FROM sys_user u " +
            "JOIN sys_user_role ur ON u.id = ur.user_id " +
            "JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE u.id = #{userId}")
    List<String> selectRoleCodes(Long userId);

    @Select("SELECT DISTINCT p.permission_code FROM sys_user u " +
            "JOIN sys_user_role ur ON u.id = ur.user_id " +
            "JOIN sys_role_permission rp ON ur.role_id = rp.role_id " +
            "JOIN sys_permission p ON rp.permission_id = p.id " +
            "WHERE u.id = #{userId}")
    List<String> selectPermissionCodes(Long userId);

    /**
     * 查询用户可见的菜单（仅 type=1，去掉不需要的 sys_user 表，返回渲染所需字段）
     */
    @Select("SELECT p.id, p.permission_name,p.url, p.icon, p.parent_id, p.sort " +
            "FROM sys_user_role ur " +
            "JOIN sys_role_permission rp ON ur.role_id = rp.role_id " +
            "JOIN sys_permission p ON rp.permission_id = p.id " +
            "WHERE ur.user_id = #{userId} AND p.type = 1 " +
            "ORDER BY p.sort ASC")
    List<UserAuthResponse> auth(Long userId);
}
