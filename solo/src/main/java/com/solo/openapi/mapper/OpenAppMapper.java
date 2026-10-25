package com.solo.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.openapi.entity.OpenApp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OpenAppMapper extends BaseMapper<OpenApp> {

    /**
     * 含密钥密文等 select=false 字段，仅供鉴权加载档案用。
     */
    @Select("SELECT * FROM open_app WHERE app_key = #{appKey} AND deleted = 0")
    OpenApp selectFullByAppKey(String appKey);

    /**
     * 含密钥密文等 select=false 字段，仅供换钥用。
     */
    @Select("SELECT * FROM open_app WHERE id = #{id} AND deleted = 0")
    OpenApp selectFullById(Long id);
}
