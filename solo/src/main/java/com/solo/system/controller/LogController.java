package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.system.entity.SysLog;
import com.solo.system.request.LogExportRequest;
import com.solo.system.request.LogPageRequest;
import com.solo.system.response.LogExcelResponse;
import com.solo.system.service.SysLogService;
import com.solo.common.utils.ExcelUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志管理
 */
@Tag(name = "操作日志管理", description = "操作日志管理")
@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class LogController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysLogService sysLogService;

    /**
     * 分页查询操作日志
     */
    @Operation(summary = "分页查询操作日志")
    @PostMapping("/page")
    @RequirePermission({"system:log:list"})
    public Result<PageResult<SysLog>> getPage(@RequestBody LogPageRequest req) {
        Page<SysLog> page = sysLogService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 导出日志为 Excel
     */
    @Operation(summary = "导出日志为 Excel")
    @PostMapping("/export")
    @RequirePermission({"system:log:export"})
    public void export(@RequestBody LogExportRequest request, HttpServletResponse response) throws IOException {
        List<SysLog> logs = sysLogService.queryForExport(request);
        List<LogExcelResponse> data = logs.stream().map(this::toExcelResponse).toList();
        ExcelUtils.export(response, "操作日志", "操作日志", data, LogExcelResponse.class);
    }

    /**
     * 批量删除日志
     */
    @Operation(summary = "批量删除日志")
    @DeleteMapping("/batch")
    @RequirePermission({"system:log:delete"})
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysLogService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除单条日志
     *
     * @param id 日志主键 ID
     */
    @Operation(summary = "删除单条日志")
    @Parameter(name = "id", description = "日志主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"system:log:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        sysLogService.removeById(id);
        return Result.success();
    }

    private LogExcelResponse toExcelResponse(SysLog log) {
        LogExcelResponse r = new LogExcelResponse();
        r.setId(log.getId());
        r.setOperatorName(log.getOperatorName());
        r.setModule(log.getModule());
        r.setBusinessType(log.getBusinessType());
        r.setDescription(log.getDescription());
        r.setRequestMethod(log.getRequestMethod());
        r.setRequestUrl(log.getRequestUrl());
        r.setResponseStatus(log.getResponseStatus() != null && log.getResponseStatus() == 1 ? "成功" : "失败");
        r.setCostMs(log.getCostMs());
        r.setClientSource(log.getClientSource());
        r.setClientIp(log.getClientIp());
        r.setErrorMsg(log.getErrorMsg());
        r.setCreateTime(log.getCreateTime() == null ? null : log.getCreateTime().format(DATE_TIME_FORMATTER));
        return r;
    }
}
