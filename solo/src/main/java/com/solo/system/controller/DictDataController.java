package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.system.entity.SysDictData;
import com.solo.system.request.DictDataPageRequest;
import com.solo.system.request.DictDataRequest;
import com.solo.system.service.SysDictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典数据管理接口（字典项）
 *
 * <p>维护字典项（"用户性别"、"设备状态" 等枚举值）。
 * 提供按 {@code dictType} 取启用项的接口（{@code GET /by-type/{dictType}}），
 * 专供前端下拉、tag、开关等控件回显使用，<b>无需鉴权</b>。</p>
 *
 * <p>典型用法：前端在管理端配置字典项，业务页打开时按 {@code dictType} 拉一次下拉源。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/dict/data/page} — 分页查询字典数据</li>
 *   <li>{@code GET  /api/dict/data/by-type/{dictType}} — 按类型取启用项（公开，前端下拉用）</li>
 *   <li>{@code POST /api/dict/data} — 新增字典数据</li>
 *   <li>{@code PUT  /api/dict/data/{id}} — 修改字典数据</li>
 *   <li>{@code DELETE /api/dict/data/batch} — 批量删除</li>
 *   <li>{@code DELETE /api/dict/data/{id}} — 删除单个</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dict/data")
@RequiredArgsConstructor
public class DictDataController {

    private final SysDictDataService sysDictDataService;

    /**
     * 分页查询字典数据
     *
     * <p>返回 {@code { total, rows }}；按 {@code dictType} 或 {@code dictLabel} 过滤。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"setting:dict:data:list"})
    public Result<PageResult<SysDictData>> getPage(@RequestBody DictDataPageRequest req) {
        Page<SysDictData> page = sysDictDataService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 按 dictType 取启用数据项
     *
     * <p>前端下拉、tag 回显专用，<b>无需登录</b>；仅返回 {@code status=1} 的项，按 sort 升序。
     * 缓存建议：同一 {@code dictType} 多次复用，可在 Pinia / localStorage 缓存。</p>
     */
    @GetMapping("/by-type/{dictType}")
    public Result<List<SysDictData>> listByType(@PathVariable String dictType) {
        return Result.success(sysDictDataService.listByType(dictType));
    }

    /**
     * 新增字典数据
     *
     * <p>{@code dictType} 必填（关联到 {@code DictTypeRequest.dictType}）；{@code dictLabel} 给前端展示，{@code dictValue} 存实际取值。</p>
     */
    @PostMapping
    @RequirePermission({"setting:dict:data:add"})
    @OperationLog(module = "字典数据", type = BusinessType.ADD, description = "新增字典数据")
    public Result<Void> save(@RequestBody DictDataRequest request) {
        sysDictDataService.createDictData(request);
        return Result.success();
    }

    /**
     * 修改字典数据
     *
     * <p>{@code {id}} 是字典项主键 ID。</p>
     */
    @PutMapping("/{id}")
    @RequirePermission({"setting:dict:data:edit"})
    @OperationLog(module = "字典数据", type = BusinessType.EDIT, description = "修改字典数据")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictDataRequest request) {
        sysDictDataService.updateDictData(id, request);
        return Result.success();
    }

    /**
     * 批量删除字典数据
     */
    @DeleteMapping("/batch")
    @RequirePermission({"setting:dict:data:delete"})
    @OperationLog(module = "字典数据", type = BusinessType.DELETE, description = "批量删除字典数据")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysDictDataService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除单个字典数据
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"setting:dict:data:delete"})
    @OperationLog(module = "字典数据", type = BusinessType.DELETE, description = "删除字典数据")
    public Result<Void> delete(@PathVariable Long id) {
        sysDictDataService.deleteDictData(id);
        return Result.success();
    }
}
