package com.solo.gb28181.controller;

import com.solo.aop.OperationLog;
import com.solo.common.Result;
import com.solo.common.enums.BusinessType;
import com.solo.gb28181.request.BroadcastStartRequest;
import com.solo.gb28181.request.BroadcastStopRequest;
import com.solo.gb28181.request.BroadcastWebRtcRequest;
import com.solo.gb28181.response.BroadcastResultResponse;
import com.solo.gb28181.service.impl.GbBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "语音广播", description = "网页麦克风实时喊话，单设备单通道单向广播")
@RestController
@RequestMapping("/api/gb28181/broadcast")
@RequiredArgsConstructor
public class GbBroadcastController {

    private final GbBroadcastService broadcastService;

    @Operation(summary = "开始广播")
    @OperationLog(module = "语音广播", type = BusinessType.OTHER, description = "开始语音广播")
    @PostMapping("/start")
    public Result<BroadcastResultResponse> start(@RequestBody BroadcastStartRequest req) {
        return Result.success(broadcastService.start(req));
    }

    @Operation(summary = "停止广播")
    @OperationLog(module = "语音广播", type = BusinessType.OTHER, description = "停止语音广播")
    @PostMapping("/stop")
    public Result<Void> stop(@RequestBody BroadcastStopRequest req) {
        broadcastService.stop(req);
        return Result.success();
    }

    @Operation(summary = "WebRTC SDP 交换")
    @PostMapping("/webrtc")
    public Result<String> webrtc(@RequestBody BroadcastWebRtcRequest req) {
        return Result.success(broadcastService.exchangeWebRtcSdp(req));
    }
}
