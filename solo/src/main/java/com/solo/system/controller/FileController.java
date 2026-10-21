package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.system.entity.SysFile;
import com.solo.system.request.FilePageRequest;
import com.solo.system.request.FileSaveRequest;
import com.solo.system.service.SysFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "文件管理", description = "文件管理")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final SysFileService sysFileService;

    @Operation(summary = "分页查询文件")
    @PostMapping("/page")
    @RequirePermission({"system:file:list"})
    public Result<PageResult<SysFile>> page(@RequestBody FilePageRequest req) {
        Page<SysFile> page = sysFileService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    @Operation(summary = "上传文件")
    @Parameter(name = "file", description = "上传的文件本体", in = ParameterIn.QUERY)
    @PostMapping("/upload")
    @RequirePermission({"system:file:upload"})
    @OperationLog(module = "文件管理", type = BusinessType.ADD, description = "上传文件", recordParam = false)
    public Result<SysFile> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(sysFileService.upload(file));
    }

    @Operation(summary = "仅落库元数据")
    @PostMapping("/meta")
    @RequirePermission({"system:file:upload"})
    @OperationLog(module = "文件管理", type = BusinessType.ADD, description = "登记文件元数据")
    public Result<SysFile> saveMeta(@RequestBody FileSaveRequest request) {
        return Result.success(sysFileService.saveMeta(request));
    }

    @Operation(summary = "删除单个文件")
    @Parameter(name = "id", description = "文件主键 ID", in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    @RequirePermission({"system:file:delete"})
    @OperationLog(module = "文件管理", type = BusinessType.DELETE, description = "删除文件")
    public Result<Void> delete(@PathVariable Long id) {
        sysFileService.deleteOne(id);
        return Result.success();
    }

    @Operation(summary = "批量删除文件")
    @DeleteMapping("/batch")
    @RequirePermission({"system:file:delete"})
    @OperationLog(module = "文件管理", type = BusinessType.DELETE, description = "批量删除文件")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysFileService.deleteBatch(ids);
        return Result.success();
    }
}
