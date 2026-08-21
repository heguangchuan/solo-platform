package com.solo.gb28181.controller;

import com.solo.common.Result;
import com.solo.gb28181.request.DownloadStartRequest;
import com.solo.gb28181.response.DownloadResultResponse;
import com.solo.gb28181.service.GbDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 录像下载控制器（薄）
 *
 * <p>仅做权限校验和入参收口，所有编排、SIP / ZLM / MinIO 交互都在 {@link GbDownloadService}。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /start} — 发起下载；立即返回带 streamId 的 INVITING 状态，
 *       业务是异步的，前端按 {@code GET /{streamId}} 轮询到 READY 取下载链接</li>
 *   <li>{@code GET /{streamId}} — 查任务状态；
 *       streamId 不存在/已清理时返回 {@code status=NOT_FOUND}</li>
 *   <li>{@code POST /{streamId}/stop} — 主动停止；发 BYE → 关端口 → 标 FAILED</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/gb28181/download")
@RequiredArgsConstructor
public class GbDownloadController {

    private final GbDownloadService downloadService;

    /** 发起录像下载（异步，立即返回当前状态） */
    @PostMapping("/start")
    public Result<DownloadResultResponse> start(@RequestBody DownloadStartRequest req) {
        return Result.success(downloadService.start(req));
    }

    /** 查任务状态 */
    @GetMapping("/{streamId}")
    public Result<DownloadResultResponse> status(@PathVariable String streamId) {
        return Result.success(downloadService.getStatus(streamId));
    }

    /** 主动停止下载 */
    @PostMapping("/{streamId}/stop")
    public Result<Void> stop(@PathVariable String streamId) {
        downloadService.stop(streamId);
        return Result.success();
    }
}
