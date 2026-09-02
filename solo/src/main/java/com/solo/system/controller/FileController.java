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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理接口
 *
 * <p>统一管理业务侧上传的文件（头像、附件、导入包等）。支持两种入桶方式：</p>
 * <ul>
 *   <li>{@code POST /upload} — 前端直接传字节，后端落桶并落库元数据</li>
 *   <li>{@code POST /meta} — 字节已通过其他方式上传到对象存储，仅调本接口同步元信息</li>
 * </ul>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/file/page} — 分页查询文件</li>
 *   <li>{@code POST /api/file/upload} — 上传文件（multipart），返回可访问 URL</li>
 *   <li>{@code POST /api/file/meta} — 仅落库元数据（已上传字节场景）</li>
 *   <li>{@code DELETE /api/file/{id}} — 删除单个文件</li>
 *   <li>{@code DELETE /api/file/batch} — 批量删除</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final SysFileService sysFileService;

    /**
     * 分页查询文件
     *
     * <p>返回 {@code { total, rows }}，按 {@code originalName} / {@code uploaderName} / 时间范围过滤。</p>
     */
    @PostMapping("/page")
    @RequirePermission({"system:file:list"})
    public Result<PageResult<SysFile>> page(@RequestBody FilePageRequest req) {
        Page<SysFile> page = sysFileService.pageQuery(req);
        return Result.success(new PageResult<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 上传文件
     *
     * <p>请求格式 {@code multipart/form-data}，字段 {@code file} 为文件本体。
     * 返回文件元数据（含 {@code id} / {@code url} / {@code originalName} / {@code size} / {@code mimeType}），
     * 前端可拿 {@code url} 直接展示或下载。</p>
     */
    @PostMapping("/upload")
    @RequirePermission({"system:file:upload"})
    @OperationLog(module = "文件管理", type = BusinessType.ADD, description = "上传文件", recordParam = false)
    public Result<SysFile> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(sysFileService.upload(file));
    }

    /**
     * 仅落库元数据
     *
     * <p>适用场景：前端/独立服务已通过其他通道把字节传到对象存储，本接口只同步元信息到本系统。
     * 请求体字段含义见 {@link FileSaveRequest}。</p>
     */
    @PostMapping("/meta")
    @RequirePermission({"system:file:upload"})
    @OperationLog(module = "文件管理", type = BusinessType.ADD, description = "登记文件元数据")
    public Result<SysFile> saveMeta(@RequestBody FileSaveRequest request) {
        return Result.success(sysFileService.saveMeta(request));
    }

    /**
     * 删除单个文件
     *
     * <p>{@code {id}} 是文件主键 ID；后端会同步清理对象存储上的字节。</p>
     */
    @DeleteMapping("/{id}")
    @RequirePermission({"system:file:delete"})
    @OperationLog(module = "文件管理", type = BusinessType.DELETE, description = "删除文件")
    public Result<Void> delete(@PathVariable Long id) {
        sysFileService.deleteOne(id);
        return Result.success();
    }

    /**
     * 批量删除文件
     *
     * <p>请求体是 ID 数组。</p>
     */
    @DeleteMapping("/batch")
    @RequirePermission({"system:file:delete"})
    @OperationLog(module = "文件管理", type = BusinessType.DELETE, description = "批量删除文件")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sysFileService.deleteBatch(ids);
        return Result.success();
    }
}
