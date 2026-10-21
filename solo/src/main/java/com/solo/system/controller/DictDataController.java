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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "字典数据管理", description = "字典数据管理")
@RestController
@RequestMapping("/api/dict/data")
@RequiredArgsConstructor
public class DictDataController {

    private final SysDictDataService sysDictDataService;

    @Operation(summary = "分页查询字典数据")
    @PostMapping("/page")
    @RequirePermission({"setting:dict:data:list"})
    public Result<PageResult<SysDictData>> getPage(@RequestBody DictDataPageRequest req) {
        Page<SysDictData> page = sysDictDataService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "按 dictType 取启用数据项")
    @Parameter(name = "dictType", description = "字典类型编码", in = ParameterIn.PATH)
    @GetMapping("/by-type/{dictType}")
    public Result<List<SysDictData>> listByType(@PathVariable String dictType) {
        return Result.success(sysDictDataService.listByType(dictType));
    }

    @Operation(summary = "新增字典数据")
    @PostMapping
    @RequirePermission({"setting:dict:data:add"})
    @OperationLog(module = "字典数据", type = BusinessType.ADD, description = "新增字典数据")
    public Result<Void> save(@RequestBody DictDataRequest request) {
        sysDictDataService.createDictData(request);
        return Result.success();
    }

    @Operation(summary = "修改字典数据")
    @Parameter(name = "id", description = "字典项主键 ID", in = ParameterIn.PATH)
    @PutMapping("/{id}")
    @RequirePermission({"setting:dict:data:edit"})
    @OperationLog(module = "字典数据", type = BusinessType.EDIT, description = "修改字典数据")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictDataRequest request) {
        sysDictDataService.updateDictData(id, request);
        return Result.success();
    }

    @Operation(summary = "批量删除字典数据")
    @DeleteMapping("/batch")
    @RequirePermission({"setting:dict:data:delete"})
    @OperationLog(module = "字典数据", type = BusinessType.DELETE, description = "批量删除字典数据")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysDictDataService.deleteBatch(ids);
        return Result.success();
    }

    @Operation(summary = "删除单个字典数据")
    @Parameter(name = "id", description = "字典项主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"setting:dict:data:delete"})
    @OperationLog(module = "字典数据", type = BusinessType.DELETE, description = "删除字典数据")
    public Result<Void> delete(@PathVariable Long id) {
        sysDictDataService.deleteDictData(id);
        return Result.success();
    }
}
