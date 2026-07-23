package com.solo.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.media.entity.MediaServer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流媒体节点 Mapper
 */
@Mapper
public interface GbMediaServerMapper extends BaseMapper<MediaServer> {
}
