package com.solo.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.openapi.entity.OpenWebhookEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpenWebhookEventMapper extends BaseMapper<OpenWebhookEvent> {
}
