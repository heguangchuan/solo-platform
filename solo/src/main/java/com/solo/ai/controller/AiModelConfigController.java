package com.solo.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.ai.entity.AiModelConfig;
import com.solo.ai.llm.AiModelProvider;
import com.solo.ai.request.AiModelConfigPageRequest;
import com.solo.ai.request.AiModelConfigRequest;
import com.solo.ai.response.AiModelConfigResponse;
import com.solo.ai.response.AiModelProviderOptionResponse;
import com.solo.ai.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * AI 模型配置（多厂商 API Key 管理）
 *
 * <p>前端"AI 模型配置"页用这套接口：让管理员预先把各家厂商的 API Key 录入系统，
 * 后续对话 / 生图 / 生视频都从这些配置里挑一个使用。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/ai/model-config/page} — 分页查询模型配置（多条件过滤）</li>
 *   <li>{@code GET /api/ai/model-config/list} — 获取所有启用的配置（下拉选择用）</li>
 *   <li>{@code GET /api/ai/model-config/provider-options} — 获取支持的全部厂商选项（新增配置时下拉用）</li>
 *   <li>{@code GET /api/ai/model-config/{id}} — 配置详情</li>
 *   <li>{@code POST /api/ai/model-config} — 新增配置</li>
 *   <li>{@code PUT /api/ai/model-config/{id}} — 修改配置</li>
 *   <li>{@code DELETE /api/ai/model-config/{id}} — 删除配置</li>
 *   <li>{@code DELETE /api/ai/model-config/batch} — 批量删除配置</li>
 * </ul>
 *
 * <h3>API Key 安全约定</h3>
 * <ul>
 *   <li>响应一律用 {@link AiModelConfigResponse}（只暴露 {@code hasApiKey} 布尔位，<b>真实密钥不出库</b>）</li>
 *   <li>前端在编辑页如果 {@code hasApiKey=true}，应展示为"已配置（留空表示不修改）"；
 *       {@code hasApiKey=false} 时才需要让用户重新填</li>
 *   <li>新增 / 修改请求体的 {@code apiKey} 字段是明文，HTTP 走 HTTPS 才安全</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai/model-config")
@RequiredArgsConstructor
public class AiModelConfigController {

    private final AiModelConfigService aiModelConfigService;

    /**
     * 分页查询模型配置
     * <p>支持按厂商 / 模型类型 / 模型名模糊 / 状态 过滤；分页参数见 {@link AiModelConfigPageRequest}。
     * 返回的 {@code hasApiKey} 是布尔位，前端表格里给一列"已配置 API Key ✓/✗"即可。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<PageResult<AiModelConfigResponse>> getPage(@RequestBody AiModelConfigPageRequest req) {
        Page<AiModelConfig> page = aiModelConfigService.pageQuery(req);
        List<AiModelConfigResponse> rows = page.getRecords().stream().map(AiModelConfigResponse::from).toList();
        return Result.success(new PageResult<>(page.getTotal(), rows));
    }

    /**
     * 获取所有启用的配置列表
     * <p>只返回 {@code status=1} 的记录，按 {@code sort} 升序。
     * 对话 / 生图 / 生视频页在"选择模型"下拉里调这个接口。</p>
     */
    @GetMapping("/list")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<List<AiModelConfigResponse>> getList() {
        List<AiModelConfigResponse> rows = aiModelConfigService.getEnabledList().stream().map(AiModelConfigResponse::from).toList();
        return Result.success(rows);
    }

    /**
     * 获取支持的厂商选项
     * <p>系统内置的全部厂商列表，给"新增配置"页的"厂商"下拉用。当前支持：Agnes 视频、OpenAI、
     * DeepSeek、Kimi / 月之暗面、通义千问 / 阿里百炼、智谱 GLM、Ollama（本地）、Groq、Mistral、
     * Xinference、硅基流动、Perplexity、Together AI、OpenRouter、Anthropic / Claude、
     * Google Gemini、自定义 OpenAI 兼容接入点。</p>
     */
    @GetMapping("/provider-options")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<List<AiModelProviderOptionResponse>> getProviderOptions() {
        List<AiModelProviderOptionResponse> rows = Arrays.stream(AiModelProvider.values())
                .map(AiModelProviderOptionResponse::from)
                .toList();
        return Result.success(rows);
    }

    /**
     * 配置详情
     * <p>id 不存在时返回 {@code null}，由前端判断空值给"配置已删除"提示。</p>
     */
    @GetMapping("/{id}")
    @RequirePermission({"ai:modelConfig:list"})
    public Result<AiModelConfigResponse> getById(@PathVariable Long id) {
        return Result.success(AiModelConfigResponse.from(aiModelConfigService.getById(id)));
    }

    /**
     * 新增配置
     * <p>必填项：{@code provider} / {@code modelName} / {@code modelType} / {@code apiKey}。
     * 选填项：{@code baseUrl}（留空用厂商默认地址） / {@code modelParams}（JSON 字符串，如 {@code {"temperature":0.7}}）/
     * {@code supportVision} / {@code status}（默认启用）/ {@code sort}。</p>
     */
    @PostMapping
    @RequirePermission({"ai:modelConfig:add"})
    @OperationLog(module = "AI模型配置", type = BusinessType.ADD, description = "新增模型配置", recordParam = false)
    public Result<Void> save(@RequestBody AiModelConfigRequest request) {
        aiModelConfigService.createConfig(request);
        return Result.success();
    }

    /**
     * 修改配置
     * <p>{@code apiKey} 留空 → 保留原值；填了新值才覆盖。明文密钥只走 HTTPS 传输。</p>
     */
    @PutMapping("/{id}")
    @RequirePermission({"ai:modelConfig:edit"})
    @OperationLog(module = "AI模型配置", type = BusinessType.EDIT, description = "修改模型配置", recordParam = false)
    public Result<Void> update(@PathVariable Long id, @RequestBody AiModelConfigRequest request) {
        aiModelConfigService.updateConfig(id, request);
        return Result.success();
    }

    /**
     * 删除配置
     * <p>已被对话 / 任务引用时也能删（应用层不强制外键），由调用方自己保证不被误删。
     * 删除后历史任务的产物不会被清理，但用此配置新建任务会失败。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"ai:modelConfig:delete"})
    @OperationLog(module = "AI模型配置", type = BusinessType.DELETE, description = "删除模型配置")
    public Result<Void> delete(@PathVariable Long id) {
        aiModelConfigService.removeById(id);
        return Result.success();
    }

    /**
     * 批量删除配置
     * <p>id 列表里包含不存在的 id 时静默忽略；空列表也是合法请求（不会报错）。</p>
     */
    @DeleteMapping("/batch")
    @RequirePermission({"ai:modelConfig:delete"})
    @OperationLog(module = "AI模型配置", type = BusinessType.DELETE, description = "批量删除模型配置")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        aiModelConfigService.removeByIds(ids);
        return Result.success();
    }
}
