package com.solo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.entity.AiProviderTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI供应商异步任务Mapper接口。
 */
@Mapper
public interface AiProviderTaskMapper extends BaseMapper<AiProviderTask> {
}
