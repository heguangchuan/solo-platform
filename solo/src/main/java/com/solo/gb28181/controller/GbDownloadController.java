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
 * 录像下载管理
 */
@RestController
@RequestMapping("/api/gb28181/download")
@RequiredArgsConstructor
public class GbDownloadController {

    private final GbDownloadService downloadService;

    /**
     * 发起录像下载
     */
    @PostMapping("/start")
    public Result<DownloadResultResponse> start(@RequestBody DownloadStartRequest req) {
        return Result.success(downloadService.start(req));
    }

    /**
     * 查任务状态
     *
     * @param streamId 任务 ID
     */
    @GetMapping("/{streamId}")
    public Result<DownloadResultResponse> status(@PathVariable String streamId) {
        return Result.success(downloadService.getStatus(streamId));
    }

    /**
     * 主动停止下载
     *
     * @param streamId 任务 ID
     */
    @PostMapping("/{streamId}/stop")
    public Result<Void> stop(@PathVariable String streamId) {
        downloadService.stop(streamId);
        return Result.success();
    }
}
