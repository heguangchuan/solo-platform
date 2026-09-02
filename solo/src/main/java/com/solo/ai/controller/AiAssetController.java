package com.solo.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.ai.entity.AiAsset;
import com.solo.ai.request.AiAssetPageRequest;
import com.solo.ai.request.AiAssetRequest;
import com.solo.ai.service.AiAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 素材库（图片 / 视频 / 音频 / 文档）
 *
 * <p>前端 AI 创作产生的图片、视频、音频，以及系统上传的素材都汇总在这里管理。
 * 文件本体存 MinIO，本接口只暴露元数据 + 访问 URL。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/ai/asset/page} — 分页查询素材（按文件名 / 类型 / 来源 过滤）</li>
 *   <li>{@code GET /api/ai/asset/{id}} — 素材详情</li>
 *   <li>{@code POST /api/ai/asset} — 新增素材（系统上传 / 模型生成结果落库用）</li>
 *   <li>{@code PUT /api/ai/asset/{id}} — 修改素材元数据</li>
 *   <li>{@code DELETE /api/ai/asset/{id}} — 删除单个素材</li>
 *   <li>{@code DELETE /api/ai/asset/batch} — 批量删除素材</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai/asset")
@RequiredArgsConstructor
public class AiAssetController {

    private final AiAssetService aiAssetService;

    /**
     * 分页查询素材
     * <p>支持按文件名 / 类型 / 来源 过滤，分页参数见 {@link AiAssetPageRequest}。
     * 响应中 {@code fileUrl} / {@code coverUrl} 是可直接放到 {@code <img>} / {@code <video>} 的访问地址。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"ai:asset:list"})
    public Result<PageResult<AiAsset>> getPage(@RequestBody AiAssetPageRequest req) {
        Page<AiAsset> page = aiAssetService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 获取素材详情
     * <p>素材 id 不存在时返回 {@code null}，由前端判断空值给出"素材已删除"提示。</p>
     */
    @GetMapping("/{id}")
    @RequirePermission({"ai:asset:list"})
    public Result<AiAsset> getById(@PathVariable Long id) {
        return Result.success(aiAssetService.getById(id));
    }

    /**
     * 新增素材
     * <p>用于把系统上传的文件、或模型生成的产物落库。{@code source} 区分 {@code SYSTEM_UPLOAD} /
     * {@code MODEL_GENERATE}，前端按这个值给素材打来源标签。</p>
     */
    @PostMapping
    @RequirePermission({"ai:asset:add"})
    @OperationLog(module = "AI素材", type = BusinessType.ADD, description = "新增素材")
    public Result<Void> save(@RequestBody AiAssetRequest request) {
        aiAssetService.createAsset(request);
        return Result.success();
    }

    /**
     * 修改素材元数据
     * <p>只能改名称 / 类型 / 来源 / 封面 URL，文件 URL 改不了。如要换文件，请重新上传再调用新增。</p>
     */
    @PutMapping("/{id}")
    @RequirePermission({"ai:asset:edit"})
    @OperationLog(module = "AI素材", type = BusinessType.EDIT, description = "修改素材")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiAssetRequest request) {
        aiAssetService.updateAsset(id, request);
        return Result.success();
    }

    /**
     * 删除单个素材
     * <p>只删除元数据记录，MinIO 上的文件对象保留（避免误删共享资源）。
     * 物理文件清理由后台定时任务处理。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"ai:asset:delete"})
    @OperationLog(module = "AI素材", type = BusinessType.DELETE, description = "删除素材")
    public Result<Void> delete(@PathVariable Long id) {
        aiAssetService.removeById(id);
        return Result.success();
    }

    /**
     * 批量删除素材
     * <p>id 列表里包含不存在的 id 时静默忽略；空列表也是合法请求（不会报错）。</p>
     */
    @DeleteMapping("/batch")
    @RequirePermission({"ai:asset:delete"})
    @OperationLog(module = "AI素材", type = BusinessType.DELETE, description = "批量删除素材")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        aiAssetService.removeByIds(ids);
        return Result.success();
    }
}
