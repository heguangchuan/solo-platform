package com.solo.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.system.entity.SysUser;
import com.solo.system.response.UserStatusCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT status, COUNT(*) AS count FROM sys_user WHERE deleted = 0 GROUP BY status")
    List<UserStatusCountVO> selectStatusCount();
}
