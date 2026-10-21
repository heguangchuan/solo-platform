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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "字典类型管理", description = "字典类型管理")
@RestController
@RequestMapping("/api/dict/type")
@RequiredArgsConstructor
public class DictTypeController {

    private final SysDictTypeService sysDictTypeService;

    @Operation(summary = "分页查询字典类型")
    @PostMapping("/page")
    @RequirePermission({"setting:dict:type:list"})
    public Result<PageResult<SysDictType>> getPage(@RequestBody DictTypePageRequest req) {
        Page<SysDictType> page = sysDictTypeService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "全量启用类型列表")
    @GetMapping("/list")
    @RequirePermission({"setting:dict:type:list"})
    public Result<List<SysDictType>> list() {
        List<SysDictType> types = sysDictTypeService.lambdaQuery()
                .eq(SysDictType::getStatus, 1)
                .orderByAsc(SysDictType::getId)
                .list();
        return Result.success(types);
    }

    @Operation(summary = "新增字典类型")
    @PostMapping
    @RequirePermission({"setting:dict:type:add"})
    @OperationLog(module = "字典类型", type = BusinessType.ADD, description = "新增字典类型")
    public Result<Void> save(@RequestBody DictTypeRequest request) {
        sysDictTypeService.createDictType(request);
        return Result.success();
    }

    @Operation(summary = "修改字典类型")
    @Parameter(name = "id", description = "字典类型主键 ID", in = ParameterIn.PATH)
    @PutMapping("/{id}")
    @RequirePermission({"setting:dict:type:edit"})
    @OperationLog(module = "字典类型", type = BusinessType.EDIT, description = "修改字典类型")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictTypeRequest request) {
        sysDictTypeService.updateDictType(id, request);
        return Result.success();
    }

    @Operation(summary = "批量删除字典类型")
    @DeleteMapping("/batch")
    @RequirePermission({"setting:dict:type:delete"})
    @OperationLog(module = "字典类型", type = BusinessType.DELETE, description = "批量删除字典类型")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysDictTypeService.deleteBatch(ids);
        return Result.success();
    }

    @Operation(summary = "删除单个字典类型")
    @Parameter(name = "id", description = "字典类型主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"setting:dict:type:delete"})
    @OperationLog(module = "字典类型", type = BusinessType.DELETE, description = "删除字典类型")
    public Result<Void> delete(@PathVariable Long id) {
        sysDictTypeService.deleteDictType(id);
        return Result.success();
    }
}
