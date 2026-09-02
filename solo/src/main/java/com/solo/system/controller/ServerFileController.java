package com.solo.system.controller;

import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.enums.BusinessType;
import com.solo.common.Result;
import com.solo.system.request.ServerFileListRequest;
import com.solo.system.request.ServerFileOperationRequest;
import com.solo.system.response.ServerFileResponse;
import com.solo.system.service.ServerFileService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 服务器文件管理
 */
@RestController
@RequestMapping("/api/server/file")
@RequiredArgsConstructor
public class ServerFileController {

    private final ServerFileService serverFileService;

    /**
     * 列远端目录
     */
    @PostMapping("/list")
    @RequirePermission({"server:file:list"})
    public Result<List<ServerFileResponse>> list(@RequestBody ServerFileListRequest request) {
        return Result.success(serverFileService.listFiles(request.getServerId(), request.getPath()));
    }

    /**
     * 上传文件到远端路径
     *
     * @param serverId 服务器主键 ID
     * @param path 远端目录绝对路径
     * @param file 上传的文件
     */
    @PostMapping("/upload")
    @RequirePermission({"server:file:upload"})
    @OperationLog(module = "服务器文件管理", type = BusinessType.IMPORT, description = "上传服务器文件", recordParam = false)
    public Result<Void> upload(@RequestParam("serverId") Long serverId,
                               @RequestParam("path") String path,
                               @RequestParam("file") MultipartFile file) throws IOException {
        serverFileService.upload(serverId, path, file);
        return Result.success();
    }

    /**
     * 下载远端文件
     */
    @PostMapping("/download")
    @RequirePermission({"server:file:download"})
    @OperationLog(module = "服务器文件管理", type = BusinessType.EXPORT, description = "下载服务器文件", recordParam = false)
    public void download(@RequestBody ServerFileOperationRequest request, HttpServletResponse response) throws IOException {
        serverFileService.download(request.getServerId(), request.getPath(), response);
    }

    /**
     * 删除远端文件或目录
     */
    @DeleteMapping
    @RequirePermission({"server:file:delete"})
    @OperationLog(module = "服务器文件管理", type = BusinessType.DELETE, description = "删除服务器文件", recordParam = false)
    public Result<Void> delete(@RequestBody ServerFileOperationRequest request) {
        serverFileService.delete(request.getServerId(), request.getPath());
        return Result.success();
    }
}
