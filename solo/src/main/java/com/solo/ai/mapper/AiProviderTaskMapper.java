package com.solo.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.ai.entity.AiProviderTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI供应商异步任务Mapper接口。
 */
@Mapper
public interface AiProviderTaskMapper extends BaseMapper<AiProviderTask> {
}
