package com.solo.onvif.controller;

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
 *
 * <p>对前端只暴露"开始 / 停止"两个动作。开始点播后，服务端会把设备 RTSP 源拉入流媒体服务器，
 * 并返回 HTTP-FLV / WS-FLV / HLS / WebRTC 四种播放 URL；前端用任一协议播放即可。
 * 返回的 streamId 形如 {@code onvif_<deviceId>}，停止时原样回传。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /start} — 开始点播；幂等，重复调直接返回原 URL（不会重复拉流）</li>
 *   <li>{@code POST /stop} — 停止点播（让流媒体服务器摘掉这条 RTSP 拉流）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/onvif/play")
@RequiredArgsConstructor
public class OnvifPlayController {

    private final OnvifPlayService onvifPlayService;

    /**
     * 开始点播
     * <p>幂等 —— 同一台设备已在推流时再次调用会直接返回原 URL，不会重复拉流；
     * 设备离线时返回业务异常，UI 需提示"设备离线"并允许用户先去刷新设备状态。</p>
     */
    @PostMapping("/start")
    public Result<OnvifPlayResultResponse> start(@RequestBody OnvifPlayStartRequest req) {
        return Result.success(onvifPlayService.start(req));
    }

    /**
     * 停止点播
     * <p>幂等 —— streamId 不存在或会话已清理时直接返回成功，不抛异常。
     * 前端可在用户关闭预览窗口时放心调用。</p>
     */
    @PostMapping("/stop")
    public Result<Void> stop(@RequestBody OnvifPlayStopRequest req) {
        onvifPlayService.stop(req);
        return Result.success();
    }
}
