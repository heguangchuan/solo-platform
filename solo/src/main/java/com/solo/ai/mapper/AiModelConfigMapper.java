package com.solo.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.ai.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI模型配置Mapper接口
 */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {
}
