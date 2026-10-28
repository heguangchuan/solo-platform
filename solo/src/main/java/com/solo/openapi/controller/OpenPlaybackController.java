package com.solo.openapi.controller;

import com.solo.gb28181.request.PlaybackControlRequest;
import com.solo.gb28181.request.PlaybackStartRequest;
import com.solo.gb28181.request.PlaybackStopRequest;
import com.solo.gb28181.request.RecordInfoQueryRequest;
import com.solo.gb28181.response.PlaybackResultResponse;
import com.solo.gb28181.response.RecordItemResponse;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.web.OpenApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放 API 历史回放。复用 {@link GbPlaybackService}，入参出参与内部管理端一致。
 */
@Tag(name = "开放API-历史回放", description = "录像查询、回放开始/停止/控制")
@RestController
@RequestMapping("/open-api/v1/playback")
@RequiredArgsConstructor
public class OpenPlaybackController {

    private final GbPlaybackService playbackService;

    @Operation(summary = "设备录像查询")
    @RequireScope(value = OpenApiConstants.SCOPE_RECORD_READ, name = "设备录像查询")
    @PostMapping("/records")
    public OpenApiResult<List<RecordItemResponse>> records(@RequestBody RecordInfoQueryRequest req,
                                                           HttpServletRequest request) {
        return OpenApiResult.success(playbackService.queryRecordInfo(req), requestId(request));
    }

    @Operation(summary = "开始回放")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAYBACK_CTRL, name = "历史回放控制")
    @PostMapping("/start")
    public OpenApiResult<PlaybackResultResponse> start(@RequestBody PlaybackStartRequest req,
                                                       HttpServletRequest request) {
        return OpenApiResult.success(playbackService.start(req), requestId(request));
    }

    @Operation(summary = "停止回放")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAYBACK_CTRL, name = "历史回放控制")
    @PostMapping("/stop")
    public OpenApiResult<Void> stop(@RequestBody PlaybackStopRequest req, HttpServletRequest request) {
        playbackService.stop(req);
        return OpenApiResult.success(null, requestId(request));
    }

    @Operation(summary = "回放控制")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAYBACK_CTRL, name = "历史回放控制")
    @PostMapping("/control")
    public OpenApiResult<Void> control(@RequestBody PlaybackControlRequest req, HttpServletRequest request) {
        playbackService.control(req);
        return OpenApiResult.success(null, requestId(request));
    }

    private static String requestId(HttpServletRequest request) {
        Object rid = request.getAttribute(OpenApiConstants.HEADER_REQUEST_ID);
        return rid != null ? rid.toString() : request.getHeader(OpenApiConstants.HEADER_REQUEST_ID);
    }
}
