package com.solo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI消息Mapper接口
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
