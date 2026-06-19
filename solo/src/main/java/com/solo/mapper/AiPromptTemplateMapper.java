package com.solo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.entity.AiPromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板Mapper接口
 */
@Mapper
public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplate> {
}
