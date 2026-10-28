package com.solo.openapi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.openapi.entity.OpenApiLog;
import com.solo.openapi.request.OpenApiLogPageRequest;

import java.util.List;

/**
 * 开放 API 调用日志查询。与只做异步写入的 {@link OpenApiAuditService} 职责分离。
 */
public interface OpenApiLogService extends IService<OpenApiLog> {

    Page<OpenApiLog> pageQuery(OpenApiLogPageRequest req);

    /** 按筛选条件全量导出，不分页 */
    List<OpenApiLog> queryForExport(OpenApiLogPageRequest req);
}
