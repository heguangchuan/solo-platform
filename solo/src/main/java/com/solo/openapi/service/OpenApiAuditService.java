package com.solo.openapi.service;

import com.solo.openapi.entity.OpenApiLog;

/**
 * 开放 API 调用审计。
 */
public interface OpenApiAuditService {

    void recordAsync(OpenApiLog log);
}
