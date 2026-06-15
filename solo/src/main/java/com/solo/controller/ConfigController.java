package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysConfig;
import com.solo.request.ConfigExportRequest;
import com.solo.request.ConfigPageRequest;
import com.solo.request.ConfigRequest;
import com.solo.response.ConfigExcelResponse;
import com.solo.service.SysConfigService;
import com.solo.utils.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 系统参数管理控制器
 */
@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class ConfigController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysConfigService sysConfigService;

    @PostMapping("/page")
    @RequirePermission({"system:config:list"})
    public Result<PageResult<SysConfig>> getPage(@RequestBody ConfigPageRequest req) {
        Page<SysConfig> page = sysConfigService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @PostMapping("/export")
    @RequirePermission({"system:config:export"})
    public void export(@RequestBody ConfigExportRequest request, HttpServletResponse response) throws IOException {
        List<SysConfig> configs = queryExportConfigs(request);
        List<ConfigExcelResponse> data = configs.stream().map(this::toExcelResponse).toList();
        ExcelUtils.export(response, "参数列表", "参数列表", data, ConfigExcelResponse.class);
    }

    private List<SysConfig> queryExportConfigs(ConfigExportRequest request) {
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            return sysConfigService.listByIds(request.getIds());
        }
        request.setPage(1L);
        request.setPageSize(Long.MAX_VALUE);
        return sysConfigService.pageQuery(request).getRecords();
    }

    private ConfigExcelResponse toExcelResponse(SysConfig config) {
        ConfigExcelResponse response = new ConfigExcelResponse();
        response.setId(config.getId());
        response.setConfigName(config.getConfigName());
        response.setConfigKey(config.getConfigKey());
        response.setConfigValue(config.getConfigValue());
        response.setConfigType(config.getConfigType() != null && config.getConfigType() == 1 ? "是" : "否");
        response.setRemark(config.getRemark());
        response.setCreateTime(config.getCreateTime() == null ? null : config.getCreateTime().format(DATE_TIME_FORMATTER));
        return response;
    }

    @PostMapping
    @RequirePermission({"system:config:add"})
    public Result<Void> save(@RequestBody ConfigRequest request) {
        sysConfigService.createConfig(request);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission({"system:config:edit"})
    public Result<Void> update(@PathVariable Long id, @RequestBody ConfigRequest request) {
        sysConfigService.updateConfig(id, request);
        return Result.success();
    }

    @DeleteMapping("/batch")
    @RequirePermission({"system:config:delete"})
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysConfigService.deleteBatch(ids);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission({"system:config:delete"})
    public Result<Void> delete(@PathVariable Long id) {
        sysConfigService.deleteConfig(id);
        return Result.success();
    }
}
