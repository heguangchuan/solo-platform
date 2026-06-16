package com.solo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.BusinessType;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.entity.SysFile;
import com.solo.request.FilePageRequest;
import com.solo.request.FileSaveRequest;
import com.solo.service.SysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理：上传、查询、删除。
 */
@RestController
@RequestMapping("/api/system/file")
@RequiredArgsConstructor
public class FileController {

    private final SysFileService sysFileService;

    /**
     * 分页查询。
     */
    @PostMapping("/page")
    @RequirePermission({"system:file:list"})
    public Result<PageResult<SysFile>> page(@RequestBody FilePageRequest req) {
        Page<SysFile> page = sysFileService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 上传文件并返回可访问 URL。
     */
    @PostMapping("/upload")
    @RequirePermission({"system:file:upload"})
    @OperationLog(module = "文件管理", type = BusinessType.ADD, description = "上传文件", recordParam = false)
    public Result<SysFile> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(sysFileService.upload(file));
    }

    /**
     * 仅落库元数据（前端/独立服务已上传字节，只调本接口同步元信息）。
     */
    @PostMapping("/meta")
    @RequirePermission({"system:file:upload"})
    @OperationLog(module = "文件管理", type = BusinessType.ADD, description = "登记文件元数据")
    public Result<SysFile> saveMeta(@RequestBody FileSaveRequest request) {
        return Result.success(sysFileService.saveMeta(request));
    }

    /**
     * 删除单个。
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:file:delete"})
    @OperationLog(module = "文件管理", type = BusinessType.DELETE, description = "删除文件")
    public Result<Void> delete(@PathVariable Long id) {
        sysFileService.deleteOne(id);
        return Result.success();
    }

    /**
     * 批量删除。
     */
    @DeleteMapping("/batch")
    @RequirePermission({"system:file:delete"})
    @OperationLog(module = "文件管理", type = BusinessType.DELETE, description = "批量删除文件")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysFileService.deleteBatch(ids);
        return Result.success();
    }
}
