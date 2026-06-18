package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysDictType;
import com.solo.request.DictTypePageRequest;
import com.solo.request.DictTypeRequest;
import com.solo.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典类型管理控制器
 */
@RestController
@RequestMapping("/api/dict/type")
@RequiredArgsConstructor
public class DictTypeController {

    private final SysDictTypeService sysDictTypeService;

    @PostMapping("/page")
    @RequirePermission({"system:dict:type:list"})
    public Result<PageResult<SysDictType>> getPage(@RequestBody DictTypePageRequest req) {
        Page<SysDictType> page = sysDictTypeService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 全量启用类型列表（用于字典数据页的类型筛选下拉）
     */
    @GetMapping("/list")
    @RequirePermission({"system:dict:type:list"})
    public Result<List<SysDictType>> list() {
        List<SysDictType> types = sysDictTypeService.lambdaQuery()
                .eq(SysDictType::getStatus, 1)
                .orderByAsc(SysDictType::getId)
                .list();
        return Result.success(types);
    }

    @PostMapping
    @RequirePermission({"system:dict:type:add"})
    @OperationLog(module = "字典类型", type = BusinessType.ADD, description = "新增字典类型")
    public Result<Void> save(@RequestBody DictTypeRequest request) {
        sysDictTypeService.createDictType(request);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission({"system:dict:type:edit"})
    @OperationLog(module = "字典类型", type = BusinessType.EDIT, description = "修改字典类型")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictTypeRequest request) {
        sysDictTypeService.updateDictType(id, request);
        return Result.success();
    }

    @DeleteMapping("/batch")
    @RequirePermission({"system:dict:type:delete"})
    @OperationLog(module = "字典类型", type = BusinessType.DELETE, description = "批量删除字典类型")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysDictTypeService.deleteBatch(ids);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission({"system:dict:type:delete"})
    @OperationLog(module = "字典类型", type = BusinessType.DELETE, description = "删除字典类型")
    public Result<Void> delete(@PathVariable Long id) {
        sysDictTypeService.deleteDictType(id);
        return Result.success();
    }
}
