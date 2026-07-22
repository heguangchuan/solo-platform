package com.solo.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.ai.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI会话Mapper接口
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
