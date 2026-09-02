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
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志接口
 *
 * <p>审计页用：分页查询、删除、Excel 导出操作日志。
 * 仅查询/删除/导出，<b>不写日志</b>——避免对日志表自身的删除/导出再产生日志噪声。</p>
 *
 * <p>支持按操作人、模块、业务类型、响应状态、客户来源、时间范围过滤。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/log/page} — 分页查询操作日志</li>
 *   <li>{@code POST /api/log/export} — 导出日志为 Excel（流式输出）</li>
 *   <li>{@code DELETE /api/log/batch} — 批量删除</li>
 *   <li>{@code DELETE /api/log/{id}} — 删除单条</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class LogController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysLogService sysLogService;

    /**
     * 分页查询操作日志
     *
     * <p>返回 {@code { total, rows }}；{@code responseStatus}：{@code 0=失败}、{@code 1=成功}；
     * {@code clientSource} 形如 {@code web} / {@code app}。
     * {@code startTime} / {@code endTime} 按 {@code yyyy-MM-dd HH:mm:ss}（GMT+8）解析。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"system:log:list"})
    public Result<PageResult<SysLog>> getPage(@RequestBody LogPageRequest req) {
        Page<SysLog> page = sysLogService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 导出日志为 Excel
     *
     * <p>响应为二进制流（{@code Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}），
     * 浏览器直接触发文件下载。导出范围与分页条件一致；{@code responseStatus} 在 Excel 中以"成功"/"失败"文本呈现。</p>
     */
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
    @DeleteMapping("/batch")
    @RequirePermission({"system:log:delete"})
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysLogService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除单条日志
     */
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
