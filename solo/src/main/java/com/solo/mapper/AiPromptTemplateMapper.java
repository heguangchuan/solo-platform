package com.solo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.entity.AiPromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * Prompt模板Mapper接口
 */
@Mapper
public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplate> {
}
