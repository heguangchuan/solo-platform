package com.solo.onvif.controller;

import com.solo.common.Result;
import com.solo.onvif.request.OnvifPlayStartRequest;
import com.solo.onvif.request.OnvifPlayStopRequest;
import com.solo.onvif.response.OnvifPlayResultResponse;
import com.solo.onvif.service.OnvifPlayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ONVIF 实时点播管理", description = "ONVIF 实时点播管理")
@RestController
@RequestMapping("/api/onvif/play")
@RequiredArgsConstructor
public class OnvifPlayController {

    private final OnvifPlayService onvifPlayService;

    @Operation(summary = "开始点播")
    @PostMapping("/start")
    public Result<OnvifPlayResultResponse> start(@RequestBody OnvifPlayStartRequest req) {
        return Result.success(onvifPlayService.start(req));
    }

    @Operation(summary = "停止点播")
    @PostMapping("/stop")
    public Result<Void> stop(@RequestBody OnvifPlayStopRequest req) {
        onvifPlayService.stop(req);
        return Result.success();
    }
}
