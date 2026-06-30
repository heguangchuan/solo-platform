package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
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
 * 实时点播控制器（薄）
 * <p>仅做权限校验和入参收口，所有编排、SIP / ZLM 交互都在 {@link GbPlayService}。</p>
 * <p>截图功能由 jessibuca 播放器自带（操作栏右键 / 工具按钮），不走后端。</p>
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
    @RequirePermission({"gb28181:play:start"})
    public Result<PlayResultResponse> start(@RequestBody PlayStartRequest req) {
        return Result.success(playService.start(req));
    }

    /**
     * 停止点播
     */
    @PostMapping("/stop")
    @RequirePermission({"gb28181:play:stop"})
    public Result<Void> stop(@RequestBody PlayStopRequest req) {
        playService.stop(req);
        return Result.success();
    }
}
