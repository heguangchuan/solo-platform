package com.solo.gb28181.controller;

import com.solo.common.Result;
import com.solo.gb28181.request.PlaybackControlRequest;
import com.solo.gb28181.request.PlaybackStartRequest;
import com.solo.gb28181.request.PlaybackStopRequest;
import com.solo.gb28181.request.RecordInfoQueryRequest;
import com.solo.gb28181.response.PlaybackResultResponse;
import com.solo.gb28181.response.RecordItemResponse;
import com.solo.gb28181.service.GbPlaybackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 历史回放控制器
 * <p>仅做权限校验和入参收口，所有业务编排都在服务层。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /query} — 录像查询（RecordInfo，按时间段取设备录像段列表）</li>
 *   <li>{@code POST /start} — 开始回放</li>
 *   <li>{@code POST /stop} — 停止回放</li>
 *   <li>{@code POST /control} — 回放控制：pause / resume / seek / speed</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/gb28181/playback")
@RequiredArgsConstructor
public class GbPlaybackController {

    private final GbPlaybackService playbackService;

    /**
     * 录像查询（按时间段向设备拉取录像段列表）
     * <p>设备无录像时返回空数组。</p>
     */
    @PostMapping("/query")
    public Result<List<RecordItemResponse>> query(@RequestBody RecordInfoQueryRequest req) {
        return Result.success(playbackService.queryRecordInfo(req));
    }

    /**
     * 开始回放
     * <p>提交后立即返回带 streamId 的 INVITING 状态，
     * 前端按 {@code GET /xxx/status} 轮询到 READY 取播放地址。
     * 失败 / 设备无录像时返回 FAILED + errorMsg。</p>
     */
    @PostMapping("/start")
    public Result<PlaybackResultResponse> start(@RequestBody PlaybackStartRequest req) {
        return Result.success(playbackService.start(req));
    }

    /**
     * 停止回放
     */
    @PostMapping("/stop")
    public Result<Void> stop(@RequestBody PlaybackStopRequest req) {
        playbackService.stop(req);
        return Result.success();
    }

    /**
     * 回放控制
     * <p>{@code action} 取值与 {@code param} 字段含义：</p>
     * <ul>
     *   <li>{@code pause} — 暂停；{@code param} 不需要</li>
     *   <li>{@code resume} — 继续；{@code param} 不需要</li>
     *   <li>{@code seek} — 跳转到相对起点的秒数；{@code param} 例 {@code "60"} 表示跳到第 60 秒</li>
     *   <li>{@code speed} — 倍速；{@code param} 例 {@code "0.5"}/{@code "1"}/{@code "2"}/{@code "4"}</li>
     * </ul>
     */
    @PostMapping("/control")
    public Result<Void> control(@RequestBody PlaybackControlRequest req) {
        playbackService.control(req);
        return Result.success();
    }
}
