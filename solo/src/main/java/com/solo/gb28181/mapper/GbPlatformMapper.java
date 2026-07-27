package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbPlatform;
import org.apache.ibatis.annotations.Mapper;

/**
 * 下级级联平台 Mapper
 */
@Mapper
public interface GbPlatformMapper extends BaseMapper<GbPlatform> {
}