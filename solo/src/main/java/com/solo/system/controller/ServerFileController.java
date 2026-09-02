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
 * 服务器文件管理接口
 *
 * <p>基于 {@code sys_server} 中维护的 SSH 凭据，远程操作目标主机文件：列目录、上传、下载、删除。
 * 前端使用流程：在 {@link ServerController} 选好目标主机 → 调本组接口按 {@code serverId} 操作。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST   /api/server/file/list} — 列目录（文件 + 子目录）</li>
 *   <li>{@code POST   /api/server/file/upload} — 上传文件到指定路径</li>
 *   <li>{@code POST   /api/server/file/download} — 下载远端文件（流式输出）</li>
 *   <li>{@code DELETE /api/server/file} — 删除远端文件或目录</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/server/file")
@RequiredArgsConstructor
public class ServerFileController {

    private final ServerFileService serverFileService;

    /**
     * 列远端目录
     *
     * <p>请求体含 {@code serverId}（目标主机 ID）和 {@code path}（远端绝对路径），
     * 返回目录下文件 + 子目录平铺列表（每个元素含 {@code name} / {@code path} / {@code directory} / {@code size} / {@code modifiedTime}）。</p>
     */
    @PostMapping("/list")
    @RequirePermission({"server:file:list"})
    public Result<List<ServerFileResponse>> list(@RequestBody ServerFileListRequest request) {
        return Result.success(serverFileService.listFiles(request.getServerId(), request.getPath()));
    }

    /**
     * 上传文件到远端路径
     *
     * <p>请求格式 {@code multipart/form-data}，字段：{@code serverId}（主机 ID）、{@code path}（远端目录绝对路径）、{@code file}（文件本体）。
     * 文件名沿用上传时 {@code file} 字段的 {@code originalFilename}。</p>
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
     *
     * <p>请求体含 {@code serverId} 和 {@code path}；响应为二进制流，浏览器直接触发文件下载。
     * 不支持下载目录（仅文件）。</p>
     */
    @PostMapping("/download")
    @RequirePermission({"server:file:download"})
    @OperationLog(module = "服务器文件管理", type = BusinessType.EXPORT, description = "下载服务器文件", recordParam = false)
    public void download(@RequestBody ServerFileOperationRequest request, HttpServletResponse response) throws IOException {
        serverFileService.download(request.getServerId(), request.getPath(), response);
    }

    /**
     * 删除远端文件或目录
     *
     * <p>请求体含 {@code serverId} 和 {@code path}；目录递归删除，建议前端弹二次确认。</p>
     */
    @DeleteMapping
    @RequirePermission({"server:file:delete"})
    @OperationLog(module = "服务器文件管理", type = BusinessType.DELETE, description = "删除服务器文件", recordParam = false)
    public Result<Void> delete(@RequestBody ServerFileOperationRequest request) {
        serverFileService.delete(request.getServerId(), request.getPath());
        return Result.success();
    }
}
