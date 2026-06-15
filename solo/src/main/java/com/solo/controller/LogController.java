package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysLog;
import com.solo.request.LogExportRequest;
import com.solo.request.LogPageRequest;
import com.solo.response.LogExcelResponse;
import com.solo.service.SysLogService;
import com.solo.utils.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志控制器
 * 仅查询/删除/导出，不写日志（避免对日志表自身的删除/导出再产生日志噪声）
 */
@RestController
@RequestMapping("/api/system/log")
@RequiredArgsConstructor
public class LogController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysLogService sysLogService;

    @PostMapping("/page")
    @RequirePermission({"system:log:list"})
    public Result<PageResult<SysLog>> getPage(@RequestBody LogPageRequest req) {
        Page<SysLog> page = sysLogService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @PostMapping("/export")
    @RequirePermission({"system:log:export"})
    public void export(@RequestBody LogExportRequest request, HttpServletResponse response) throws IOException {
        List<SysLog> logs = sysLogService.queryForExport(request);
        List<LogExcelResponse> data = logs.stream().map(this::toExcelResponse).toList();
        ExcelUtils.export(response, "操作日志", "操作日志", data, LogExcelResponse.class);
    }

    @DeleteMapping("/batch")
    @RequirePermission({"system:log:delete"})
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysLogService.deleteBatch(ids);
        return Result.success();
    }

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
