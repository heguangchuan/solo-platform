package com.solo.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.ai.entity.AiPromptTemplate;
import com.solo.ai.request.AiPromptTemplatePageRequest;
import com.solo.ai.request.AiPromptTemplateRequest;
import com.solo.ai.service.AiPromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 提示词模板管理
 *
 * <p>提示词模板是带占位符的预设文本，例：{@code "请把下面这段话翻译成英文：${text}"}。
 * 管理员在这里维护模板；用户调对话时挑一个模板，填上变量就能批量复用。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/ai/prompt-template/page} — 分页查询模板（按名称 / 编码 / 类型 / 状态 过滤）</li>
 *   <li>{@code GET /api/ai/prompt-template/list} — 获取所有启用的模板（下拉选择用）</li>
 *   <li>{@code GET /api/ai/prompt-template/{id}} — 模板详情</li>
 *   <li>{@code POST /api/ai/prompt-template} — 新增模板</li>
 *   <li>{@code PUT /api/ai/prompt-template/{id}} — 修改模板</li>
 *   <li>{@code DELETE /api/ai/prompt-template/{id}} — 删除模板</li>
 *   <li>{@code DELETE /api/ai/prompt-template/batch} — 批量删除模板</li>
 *   <li>{@code POST /api/ai/prompt-template/render/{templateCode}} — 预览渲染（变量替换后看最终提示词）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai/prompt-template")
@RequiredArgsConstructor
public class AiPromptTemplateController {

    private final AiPromptTemplateService aiPromptTemplateService;

    /**
     * 分页查询模板
     * <p>支持按 templateName / templateCode / templateType / status 多条件过滤；分页参数见 {@link AiPromptTemplatePageRequest}。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"ai:promptTemplate:list"})
    public Result<PageResult<AiPromptTemplate>> getPage(@RequestBody AiPromptTemplatePageRequest req) {
        Page<AiPromptTemplate> page = aiPromptTemplateService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 获取所有启用的模板列表
     * <p>仅 {@code status=1} 的记录，按 sort 升序。
     * 对话页 / 创作页的"选模板"下拉调这个接口。</p>
     */
    @GetMapping("/list")
    @RequirePermission({"ai:promptTemplate:list"})
    public Result<List<AiPromptTemplate>> getList() {
        return Result.success(aiPromptTemplateService.getEnabledList());
    }

    /**
     * 模板详情
     * <p>id 不存在时返回 {@code null}，由前端判断空值给"模板已删除"提示。</p>
     */
    @GetMapping("/{id}")
    @RequirePermission({"ai:promptTemplate:list"})
    public Result<AiPromptTemplate> getById(@PathVariable Long id) {
        return Result.success(aiPromptTemplateService.getById(id));
    }

    /**
     * 新增模板
     * <p>{@code content} 内可使用占位符（默认 {@code ${var}} 形式），通过 {@code variables} 字段
     * 定义变量名 / 含义 / 示例（JSON 字符串）。新增后用户在前端对话页能立即看到。</p>
     */
    @PostMapping
    @RequirePermission({"ai:promptTemplate:add"})
    @OperationLog(module = "提示词模板", type = BusinessType.ADD, description = "新增模板")
    public Result<Void> save(@RequestBody AiPromptTemplateRequest request) {
        aiPromptTemplateService.createTemplate(request);
        return Result.success();
    }

    /**
     * 修改模板
     * <p>整对象覆盖：变量 JSON 字符串一并替换。已删除的 templateCode 不可复用。</p>
     */
    @PutMapping("/{id}")
    @RequirePermission({"ai:promptTemplate:edit"})
    @OperationLog(module = "提示词模板", type = BusinessType.EDIT, description = "修改模板")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiPromptTemplateRequest request) {
        aiPromptTemplateService.updateTemplate(id, request);
        return Result.success();
    }

    /**
     * 删除模板
     * <p>仅删除元数据；历史对话 / 任务中已经展开过的提示词不受影响。
     * 删除后对话页的"模板"下拉里就不会再出现这个 templateCode。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"ai:promptTemplate:delete"})
    @OperationLog(module = "提示词模板", type = BusinessType.DELETE, description = "删除模板")
    public Result<Void> delete(@PathVariable Long id) {
        aiPromptTemplateService.removeById(id);
        return Result.success();
    }

    /**
     * 批量删除模板
     * <p>id 列表里包含不存在的 id 时静默忽略；空列表也是合法请求（不会报错）。</p>
     */
    @DeleteMapping("/batch")
    @RequirePermission({"ai:promptTemplate:delete"})
    @OperationLog(module = "提示词模板", type = BusinessType.DELETE, description = "批量删除模板")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        aiPromptTemplateService.removeByIds(ids);
        return Result.success();
    }

    /**
     * 渲染模板（变量替换预览）
     * <p>把请求体里 {@code variables}（{@code key → value}）按模板内容里的占位符替换，
     * 返回最终提示词字符串。<b>不落库</b>，只用于前端调试 / 预览。
     * 例：模板 {@code "翻译：${text}"} + variables {@code {"text":"你好"}} → 返回 {@code "翻译：你好"}。</p>
     */
    @PostMapping("/render/{templateCode}")
    @RequirePermission({"ai:promptTemplate:list"})
    public Result<String> render(@PathVariable String templateCode, @RequestBody Map<String, Object> variables) {
        String result = aiPromptTemplateService.renderTemplate(templateCode, variables);
        return Result.success(result);
    }
}
