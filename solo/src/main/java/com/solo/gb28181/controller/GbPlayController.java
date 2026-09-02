package com.solo.gb28181.controller;

import com.solo.common.Result;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.GbPlayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实时点播管理
 */
@RestController
@RequestMapping("/api/gb28181/play")
@RequiredArgsConstructor
public class GbPlayController {

    private final GbPlayService playService;

    /**
     * 开始点播
     */
    @PostMapping("/start")
    public Result<PlayResultResponse> start(@RequestBody PlayStartRequest req) {
        return Result.success(playService.start(req));
    }

    /**
     * 停止点播
     */
    @PostMapping("/stop")
    public Result<Void> stop(@RequestBody PlayStopRequest req) {
        playService.stop(req);
        return Result.success();
    }
}
