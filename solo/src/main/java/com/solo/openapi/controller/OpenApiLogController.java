package com.solo.openapi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.enums.BusinessType;
import com.solo.common.utils.ExcelUtils;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.entity.OpenApiLog;
import com.solo.openapi.request.OpenApiLogPageRequest;
import com.solo.openapi.response.OpenApiLogExcelResponse;
import com.solo.openapi.service.OpenApiLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 开放 API 调用日志管理端。只查不删，走平台内部 JWT 和权限体系。
 */
@Tag(name = "开放API调用日志", description = "第三方调用审计日志的查询与导出")
@RestController
@RequestMapping("/api/openapi/log")
@RequiredArgsConstructor
public class OpenApiLogController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OpenApiLogService logService;

    @Operation(summary = "分页查询调用日志")
    @PostMapping("/page")
    @RequirePermission("openapi:log:list")
    public Result<PageResult<OpenApiLog>> page(@RequestBody OpenApiLogPageRequest req) {
        Page<OpenApiLog> page = logService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "按筛选条件导出调用日志为 Excel")
    @OperationLog(module = "开放API日志", type = BusinessType.EXPORT, description = "导出开放API调用日志")
    @PostMapping("/export")
    @RequirePermission("openapi:log:export")
    public void export(@RequestBody OpenApiLogPageRequest req, HttpServletResponse response) throws IOException {
        List<OpenApiLog> logs = logService.queryForExport(req);
        List<OpenApiLogExcelResponse> data = logs.stream().map(this::toExcelResponse).toList();
        ExcelUtils.export(response, "开放API调用日志", "开放API调用日志", data, OpenApiLogExcelResponse.class);
    }

    private OpenApiLogExcelResponse toExcelResponse(OpenApiLog log) {
        OpenApiLogExcelResponse r = new OpenApiLogExcelResponse();
        r.setCreateTime(log.getCreateTime() == null ? null : log.getCreateTime().format(DATE_TIME_FORMATTER));
        r.setAppKey(log.getAppKey());
        r.setRequestId(log.getRequestId());
        r.setMethod(log.getMethod());
        r.setPath(log.getPath());
        r.setClientIp(log.getClientIp());
        r.setHttpStatus(log.getHttpStatus());
        r.setBizCode(log.getBizCode());
        boolean success = log.getBizCode() != null
                && log.getBizCode() == OpenApiErrorCode.SUCCESS.getCode();
        r.setResult(success ? "成功" : "失败");
        r.setCostMs(log.getCostMs());
        r.setErrorMsg(log.getErrorMsg());
        return r;
    }
}
