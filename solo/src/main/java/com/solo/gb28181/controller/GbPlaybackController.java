package com.solo.gb28181.controller;

import com.solo.aop.OperationLog;
import com.solo.common.Result;
import com.solo.common.enums.BusinessType;
import com.solo.gb28181.request.PlaybackControlRequest;
import com.solo.gb28181.request.PlaybackStartRequest;
import com.solo.gb28181.request.PlaybackStopRequest;
import com.solo.gb28181.request.RecordInfoQueryRequest;
import com.solo.gb28181.response.PlaybackResultResponse;
import com.solo.gb28181.response.RecordItemResponse;
import com.solo.gb28181.service.GbPlaybackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "历史回放管理", description = "历史回放管理")
@RestController
@RequestMapping("/api/gb28181/playback")
@RequiredArgsConstructor
public class GbPlaybackController {

    private final GbPlaybackService playbackService;

    @Operation(summary = "录像查询")
    @PostMapping("/query")
    public Result<List<RecordItemResponse>> query(@RequestBody RecordInfoQueryRequest req) {
        return Result.success(playbackService.queryRecordInfo(req));
    }

    @Operation(summary = "开始回放")
    @OperationLog(module = "历史回放", type = BusinessType.OTHER, description = "开始历史回放")
    @PostMapping("/start")
    public Result<PlaybackResultResponse> start(@RequestBody PlaybackStartRequest req) {
        return Result.success(playbackService.start(req));
    }

    @Operation(summary = "停止回放")
    @OperationLog(module = "历史回放", type = BusinessType.OTHER, description = "停止历史回放")
    @PostMapping("/stop")
    public Result<Void> stop(@RequestBody PlaybackStopRequest req) {
        playbackService.stop(req);
        return Result.success();
    }

    @Operation(summary = "回放控制")
    @OperationLog(module = "历史回放", type = BusinessType.OTHER, description = "回放控制")
    @PostMapping("/control")
    public Result<Void> control(@RequestBody PlaybackControlRequest req) {
        playbackService.control(req);
        return Result.success();
    }
}
