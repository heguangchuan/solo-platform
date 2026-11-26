package com.solo.openapi.controller;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.PlaybackControlRequest;
import com.solo.gb28181.request.PlaybackStartRequest;
import com.solo.gb28181.request.PlaybackStopRequest;
import com.solo.gb28181.request.RecordInfoQueryRequest;
import com.solo.gb28181.response.PlaybackResultResponse;
import com.solo.gb28181.response.RecordItemResponse;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.gb28181.support.GbPlayUrlSigner;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;
import com.solo.openapi.auth.OpenAppContext;
import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.exception.OpenApiException;
import com.solo.openapi.service.OpenCatalogService;
import com.solo.openapi.support.OpenApiContextHolder;
import com.solo.openapi.web.OpenApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "开放API-历史回放", description = "录像查询、回放开始/停止/控制")
@RestController
@RequestMapping("/open-api/v1/playback")
@RequiredArgsConstructor
public class OpenPlaybackController {

    private final GbPlaybackService playbackService;
    private final OpenCatalogService catalogService;
    private final ViewTokenSigner viewTokenSigner;

    @Operation(summary = "设备录像查询")
    @RequireScope(value = OpenApiConstants.SCOPE_RECORD_READ, name = "设备录像查询")
    @PostMapping("/records")
    public OpenApiResult<List<RecordItemResponse>> records(@RequestBody RecordInfoQueryRequest req,
                                                           HttpServletRequest request) {
        normalizeChannel(req);
        return OpenApiResult.success(playbackService.queryRecordInfo(req),
                OpenApiContextHolder.requestId(request));
    }

    @Operation(summary = "开始回放")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAYBACK_CTRL, name = "历史回放控制")
    @PostMapping("/start")
    public OpenApiResult<PlaybackResultResponse> start(@RequestBody PlaybackStartRequest req,
                                                       HttpServletRequest request) {
        ViewActor actor = openAppActor(request);
        normalizeChannel(req);
        PlaybackResultResponse vo = playbackService.start(req);
        GbPlayUrlSigner.sign(vo, actor, viewTokenSigner);
        return OpenApiResult.success(vo, OpenApiContextHolder.requestId(request));
    }

    private ViewActor openAppActor(HttpServletRequest request) {
        OpenAppContext ctx = OpenApiContextHolder.get(request);
        if (ctx == null) {
            throw new OpenApiException(OpenApiErrorCode.MISSING_CREDENTIAL, "缺少开放应用播放授权身份");
        }
        return ViewActor.openApp(ctx.getAppId(), ctx.getAppName());
    }

    @Operation(summary = "停止回放")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAYBACK_CTRL, name = "历史回放控制")
    @PostMapping("/stop")
    public OpenApiResult<Void> stop(@RequestBody PlaybackStopRequest req, HttpServletRequest request) {
        playbackService.stop(req);
        return OpenApiResult.success(null, OpenApiContextHolder.requestId(request));
    }

    @Operation(summary = "回放控制")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAYBACK_CTRL, name = "历史回放控制")
    @PostMapping("/control")
    public OpenApiResult<Void> control(@RequestBody PlaybackControlRequest req, HttpServletRequest request) {
        playbackService.control(req);
        return OpenApiResult.success(null, OpenApiContextHolder.requestId(request));
    }

    private void normalizeChannel(RecordInfoQueryRequest req) {
        GbDeviceChannel channel = catalogService.resolveChannel(req.getDeviceId(), req.getChannelId());
        req.setDeviceId(channel.getDeviceId());
        req.setChannelId(channel.getChannelId());
    }

    private void normalizeChannel(PlaybackStartRequest req) {
        GbDeviceChannel channel = catalogService.resolveChannel(req.getDeviceId(), req.getChannelId());
        req.setDeviceId(channel.getDeviceId());
        req.setChannelId(channel.getChannelId());
    }
}
