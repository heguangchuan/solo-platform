package com.solo.openapi.service.impl;

import com.solo.openapi.entity.OpenApiLog;
import com.solo.openapi.mapper.OpenApiLogMapper;
import com.solo.openapi.service.OpenApiAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiAuditServiceImpl implements OpenApiAuditService {

    private final OpenApiLogMapper openApiLogMapper;

    @Async("logExecutor")
    @Override
    public void recordAsync(OpenApiLog entry) {
        try {
            openApiLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("[开放API] 审计日志写入失败 requestId={}", entry.getRequestId(), e);
        }
    }
}
