package com.solo.openapi.controller;

import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.request.OpenRecordStartRequest;
import com.solo.openapi.request.OpenRecordStatusRequest;
import com.solo.openapi.request.OpenRecordStopRequest;
import com.solo.openapi.response.OpenRecordStartResponse;
import com.solo.openapi.response.OpenRecordStatusResponse;
import com.solo.openapi.service.OpenRecordService;
import com.solo.openapi.support.OpenApiContextHolder;
import com.solo.openapi.web.OpenApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 开放 API：云端录制的原子控制，录像计划由上层业务系统编排。 */
@Tag(name = "开放API-云端录制", description = "开始录制、停止录制、查询录制态")
@RestController
@RequestMapping("/open-api/v1/record")
@RequiredArgsConstructor
public class OpenRecordController {

    private final OpenRecordService recordService;

    @Operation(summary = "开始录制")
    @RequireScope(value = OpenApiConstants.SCOPE_RECORD_CTRL, name = "开始录制")
    @PostMapping("/start")
    public OpenApiResult<OpenRecordStartResponse> start(@Valid @RequestBody OpenRecordStartRequest req,
                                                        HttpServletRequest request) {
        String streamId = recordService.start(
                req.getDeviceId(), req.getChannelId(), req.getTransport(), req.getStreamNumber());
        return OpenApiResult.success(new OpenRecordStartResponse(streamId),
                OpenApiContextHolder.requestId(request));
    }

    @Operation(summary = "停止录制")
    @RequireScope(value = OpenApiConstants.SCOPE_RECORD_CTRL, name = "停止录制")
    @PostMapping("/stop")
    public OpenApiResult<Void> stop(@Valid @RequestBody OpenRecordStopRequest req,
                                    HttpServletRequest request) {
        recordService.stop(req.getStreamId());
        return OpenApiResult.success(null, OpenApiContextHolder.requestId(request));
    }

    @Operation(summary = "查询录制态")
    @RequireScope(value = OpenApiConstants.SCOPE_RECORD_READ, name = "查询录制态")
    @PostMapping("/status")
    public OpenApiResult<OpenRecordStatusResponse> status(
            @Valid @RequestBody OpenRecordStatusRequest req, HttpServletRequest request) {
        return OpenApiResult.success(recordService.status(req.getStreamId()),
                OpenApiContextHolder.requestId(request));
    }
}
