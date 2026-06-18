package com.solo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI会话Mapper接口
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
