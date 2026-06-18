package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysDictData;
import com.solo.request.DictDataPageRequest;
import com.solo.request.DictDataRequest;
import com.solo.service.SysDictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典数据管理控制器
 */
@RestController
@RequestMapping("/api/dict/data")
@RequiredArgsConstructor
public class DictDataController {

    private final SysDictDataService sysDictDataService;

    @PostMapping("/page")
    @RequirePermission({"setting:dict:data:list"})
    public Result<PageResult<SysDictData>> getPage(@RequestBody DictDataPageRequest req) {
        Page<SysDictData> page = sysDictDataService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 按 dictType 取启用数据项（前端下拉、tag 回显使用，无需鉴权）
     */
    @GetMapping("/by-type/{dictType}")
    public Result<List<SysDictData>> listByType(@PathVariable String dictType) {
        return Result.success(sysDictDataService.listByType(dictType));
    }

    @PostMapping
    @RequirePermission({"setting:dict:data:add"})
    @OperationLog(module = "字典数据", type = BusinessType.ADD, description = "新增字典数据")
    public Result<Void> save(@RequestBody DictDataRequest request) {
        sysDictDataService.createDictData(request);
        return Result.success();
    }

    @PutMapping("/{id}")
    @RequirePermission({"setting:dict:data:edit"})
    @OperationLog(module = "字典数据", type = BusinessType.EDIT, description = "修改字典数据")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictDataRequest request) {
        sysDictDataService.updateDictData(id, request);
        return Result.success();
    }

    @DeleteMapping("/batch")
    @RequirePermission({"setting:dict:data:delete"})
    @OperationLog(module = "字典数据", type = BusinessType.DELETE, description = "批量删除字典数据")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysDictDataService.deleteBatch(ids);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission({"setting:dict:data:delete"})
    @OperationLog(module = "字典数据", type = BusinessType.DELETE, description = "删除字典数据")
    public Result<Void> delete(@PathVariable Long id) {
        sysDictDataService.deleteDictData(id);
        return Result.success();
    }
}
