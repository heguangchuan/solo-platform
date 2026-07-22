package com.solo.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.ai.entity.AiGenerationTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI创作任务Mapper接口
 */
@Mapper
public interface AiGenerationTaskMapper extends BaseMapper<AiGenerationTask> {
}
