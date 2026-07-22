package com.solo.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.system.entity.SysMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {

    /**
     * 标记单条消息为已读
     */
    @Update("UPDATE sys_message SET read_flag = 1 WHERE id = #{id} AND username = #{username}")
    int markRead(@Param("id") Long id, @Param("username") String username);

    /**
     * 标记当前用户所有消息为已读
     */
    @Update("UPDATE sys_message SET read_flag = 1 WHERE username = #{username} AND read_flag = 0")
    int markAllRead(@Param("username") String username);
}
