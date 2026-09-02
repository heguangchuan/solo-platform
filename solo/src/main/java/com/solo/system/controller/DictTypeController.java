package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.system.entity.SysDictType;
import com.solo.system.request.DictTypePageRequest;
import com.solo.system.request.DictTypeRequest;
import com.solo.system.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典类型管理接口（字典大类）
 *
 * <p>维护字典的归类维度（"用户性别"、"设备状态" 等大类）。
 * {@code GET /list} 取全量启用类型，供字典数据页的类型筛选下拉使用。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/dict/type/page} — 分页查询字典类型</li>
 *   <li>{@code GET  /api/dict/type/list} — 全量启用类型列表（用于筛选下拉）</li>
 *   <li>{@code POST /api/dict/type} — 新增字典类型</li>
 *   <li>{@code PUT  /api/dict/type/{id}} — 修改字典类型</li>
 *   <li>{@code DELETE /api/dict/type/batch} — 批量删除</li>
 *   <li>{@code DELETE /api/dict/type/{id}} — 删除单个</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dict/type")
@RequiredArgsConstructor
public class DictTypeController {

    private final SysDictTypeService sysDictTypeService;

    /**
     * 分页查询字典类型
     *
     * <p>返回 {@code { total, rows }}；按 {@code dictName} / {@code dictType} / {@code status} 过滤。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"setting:dict:type:list"})
    public Result<PageResult<SysDictType>> getPage(@RequestBody DictTypePageRequest req) {
        Page<SysDictType> page = sysDictTypeService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 全量启用类型列表
     *
     * <p>仅 {@code status=1}（启用）的字典大类；用于字典数据页的"类型筛选"下拉，按 ID 升序。</p>
     */
    @GetMapping("/list")
    @RequirePermission({"setting:dict:type:list"})
    public Result<List<SysDictType>> list() {
        List<SysDictType> types = sysDictTypeService.lambdaQuery()
                .eq(SysDictType::getStatus, 1)
                .orderByAsc(SysDictType::getId)
                .list();
        return Result.success(types);
    }

    /**
     * 新增字典类型
     *
     * <p>{@code dictType} 全局唯一，新增后供 {@link DictDataController} 引用。</p>
     */
    @PostMapping
    @RequirePermission({"setting:dict:type:add"})
    @OperationLog(module = "字典类型", type = BusinessType.ADD, description = "新增字典类型")
    public Result<Void> save(@RequestBody DictTypeRequest request) {
        sysDictTypeService.createDictType(request);
        return Result.success();
    }

    /**
     * 修改字典类型
     *
     * <p>{@code {id}} 是字典类型主键 ID；改 {@code dictType} 会影响已有数据项关联，请谨慎。</p>
     */
    @PutMapping("/{id}")
    @RequirePermission({"setting:dict:type:edit"})
    @OperationLog(module = "字典类型", type = BusinessType.EDIT, description = "修改字典类型")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictTypeRequest request) {
        sysDictTypeService.updateDictType(id, request);
        return Result.success();
    }

    /**
     * 批量删除字典类型
     */
    @DeleteMapping("/batch")
    @RequirePermission({"setting:dict:type:delete"})
    @OperationLog(module = "字典类型", type = BusinessType.DELETE, description = "批量删除字典类型")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysDictTypeService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 删除单个字典类型
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"setting:dict:type:delete"})
    @OperationLog(module = "字典类型", type = BusinessType.DELETE, description = "删除字典类型")
    public Result<Void> delete(@PathVariable Long id) {
        sysDictTypeService.deleteDictType(id);
        return Result.success();
    }
}
