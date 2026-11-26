package com.solo.onvif.controller;

import com.solo.common.Result;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;
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
    private final ViewTokenSigner viewTokenSigner;

    @Operation(summary = "开始点播")
    @PostMapping("/start")
    public Result<OnvifPlayResultResponse> start(@RequestBody OnvifPlayStartRequest req) {
        ViewActor actor = viewTokenSigner.requireCurrentUserActor();
        OnvifPlayResultResponse result = onvifPlayService.start(req);
        signUrls(result, actor);
        return Result.success(result);
    }

    private void signUrls(OnvifPlayResultResponse result, ViewActor actor) {
        String serverId = result.getMediaServerId();
        String app = result.getApp();
        String streamId = result.getStreamId();
        result.setFlvUrl(viewTokenSigner.appendToken(result.getFlvUrl(), actor, PlayTarget.httpFlv(serverId, app, streamId)));
        result.setWsFlvUrl(viewTokenSigner.appendToken(result.getWsFlvUrl(), actor, PlayTarget.wsFlv(serverId, app, streamId)));
        result.setHlsUrl(viewTokenSigner.appendToken(result.getHlsUrl(), actor, PlayTarget.hls(serverId, app, streamId)));
        result.setRtcUrl(viewTokenSigner.appendToken(result.getRtcUrl(), actor, PlayTarget.rtc(serverId, app, streamId)));
    }

    @Operation(summary = "停止点播")
    @PostMapping("/stop")
    public Result<Void> stop(@RequestBody OnvifPlayStopRequest req) {
        onvifPlayService.stop(req);
        return Result.success();
    }
}
