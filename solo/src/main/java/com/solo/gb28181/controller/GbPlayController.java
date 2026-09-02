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
 * 实时点播控制器
 * <p>仅做权限校验和入参收口，所有业务编排都在服务层。</p>
 * <p>截图功能由 jessibuca 播放器自带（操作栏右键 / 工具按钮），不走后端。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /start} — 开始点播</li>
 *   <li>{@code POST /stop} — 停止点播</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/gb28181/play")
@RequiredArgsConstructor
public class GbPlayController {

    private final GbPlayService playService;

    /**
     * 开始点播
     * <p>提交后立即返回带 streamId 的 INVITING 状态，
     * 业务是异步的。前端按 {@code GET /xxx/status} 轮询到 READY 取播放地址。
     * 失败时返回 FAILED + errorMsg。</p>
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
