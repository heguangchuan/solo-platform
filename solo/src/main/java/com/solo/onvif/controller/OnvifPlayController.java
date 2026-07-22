package com.solo.onvif.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.onvif.request.OnvifPlayStartRequest;
import com.solo.onvif.request.OnvifPlayStopRequest;
import com.solo.onvif.response.OnvifPlayResultResponse;
import com.solo.onvif.service.OnvifPlayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ONVIF 实时点播
 */
@RestController
@RequestMapping("/api/onvif/play")
@RequiredArgsConstructor
public class OnvifPlayController {

    private final OnvifPlayService onvifPlayService;

    @PostMapping("/start")
    @RequirePermission({"onvif:play:start"})
    public Result<OnvifPlayResultResponse> start(@RequestBody OnvifPlayStartRequest req) {
        return Result.success(onvifPlayService.start(req));
    }

    @PostMapping("/stop")
    @RequirePermission({"onvif:play:stop"})
    public Result<Void> stop(@RequestBody OnvifPlayStopRequest req) {
        onvifPlayService.stop(req);
        return Result.success();
    }
}
