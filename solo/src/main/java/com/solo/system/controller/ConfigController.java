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
 * 系统参数管理接口
 *
 * <p>维护 key-value 型运行期参数（{@code configKey} / {@code configValue}），
 * 供业务侧按 {@code configKey} 读取，常用于阈值、开关、第三方接口配置等。
 * 管理端支持分页 + 批量删除 + Excel 导出。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/config/page} — 分页查询参数列表</li>
 *   <li>{@code POST /api/config/export} — 导出参数为 Excel（流式输出）</li>
 *   <li>{@code POST /api/config} — 新增参数</li>
 *   <li>{@code PUT  /api/config/{id}} — 修改参数</li>
 *   <li>{@code DELETE /api/config/batch} — 批量删除</li>
 *   <li>{@code DELETE /api/config/{id}} — 删除单个</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysConfigService sysConfigService;

    /**
     * 分页查询参数列表
     *
     * <p>返回 {@code { total, rows }} 形式，{@code page} 从 1 开始，{@code pageSize} 默认 20。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"setting:config:list"})
    public Result<PageResult<SysConfig>> getPage(@RequestBody ConfigPageRequest req) {
        Page<SysConfig> page = sysConfigService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 导出参数为 Excel
     *
     * <p>响应为二进制流（{@code Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}），
     * 浏览器直接触发文件下载。{@code ids} 非空时按 ID 集合导出，否则按分页条件全量导出。</p>
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
     *
     * <p>{@code configKey} 全局唯一，必填；{@code configValue} 为字符串，业务侧按需解析。</p>
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
     * <p>{@code {id}} 是参数主键 ID；{@code configKey} 修改后会影响业务侧取值，请谨慎。</p>
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
     *
     * <p>请求体是 ID 数组；返回 200 即删除完成（部分失败会以业务异常形式整体回滚）。</p>
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
     * <p>{@code {id}} 是参数主键 ID；不存在时返回 404。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"setting:config:delete"})
    @OperationLog(module = "参数管理", type = BusinessType.DELETE, description = "删除参数")
    public Result<Void> delete(@PathVariable Long id) {
        sysConfigService.deleteConfig(id);
        return Result.success();
    }
}
