package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.system.entity.SysConfig;
import com.solo.system.request.ConfigExportRequest;
import com.solo.system.request.ConfigPageRequest;
import com.solo.system.request.ConfigRequest;
import com.solo.system.response.ConfigExcelResponse;
import com.solo.system.service.SysConfigService;
import com.solo.common.utils.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 系统参数管理
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysConfigService sysConfigService;

    /**
     * 分页查询参数列表
     */
    @PostMapping("/page")
    @RequirePermission({"setting:config:list"})
    public Result<PageResult<SysConfig>> getPage(@RequestBody ConfigPageRequest req) {
        Page<SysConfig> page = sysConfigService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 导出参数为 Excel
     */
    @PostMapping("/export")
    @RequirePermission({"setting:config:export"})
    @OperationLog(module = "参数管理", type = BusinessType.EXPORT, description = "导出参数")
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
        response.setRemark(config.getRemark());
        response.setCreateTime(config.getCreateTime() == null ? null : config.getCreateTime().format(DATE_TIME_FORMATTER));
        return response;
    }

    /**
     * 新增参数
     */
    @PostMapping
    @RequirePermission({"setting:config:add"})
    @OperationLog(module = "参数管理", type = BusinessType.ADD, description = "新增参数")
    public Result<Void> save(@RequestBody ConfigRequest request) {
        sysConfigService.createConfig(request);
        return Result.success();
    }

    /**
     * 修改参数
     *
     * @param id 参数主键 ID
     */
    @PutMapping("/{id}")
    @RequirePermission({"setting:config:edit"})
    @OperationLog(module = "参数管理", type = BusinessType.EDIT, description = "修改参数")
    public Result<Void> update(@PathVariable Long id, @RequestBody ConfigRequest request) {
        sysConfigService.updateConfig(id, request);
        return Result.success();
    }

    /**
     * 批量删除参数
     */
    @DeleteMapping("/batch")
    @RequirePermission({"setting:config:delete"})
    @OperationLog(module = "参数管理", type = BusinessType.DELETE, description = "批量删除参数")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysConfigService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除单个参数
     *
     * @param id 参数主键 ID
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"setting:config:delete"})
    @OperationLog(module = "参数管理", type = BusinessType.DELETE, description = "删除参数")
    public Result<Void> delete(@PathVariable Long id) {
        sysConfigService.deleteConfig(id);
        return Result.success();
    }
}
