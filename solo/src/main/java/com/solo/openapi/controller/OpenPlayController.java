package com.solo.openapi.controller;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.support.GbPlayUrlSigner;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;
import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.auth.OpenAppContext;
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

@Tag(name = "开放API-实时点播", description = "三方调用实时点播")
@RestController
@RequestMapping("/open-api/v1/play")
@RequiredArgsConstructor
public class OpenPlayController {

    private final GbPlayService playService;
    private final OpenCatalogService catalogService;
    private final ViewTokenSigner viewTokenSigner;

    @Operation(summary = "开始点播")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAY_WRITE, name = OpenApiConstants.SCOPE_NAME_PLAY_WRITE)
    @PostMapping("/start")
    public OpenApiResult<PlayResultResponse> start(@RequestBody PlayStartRequest req, HttpServletRequest request) {
        ViewActor actor = openAppActor(request);
        // 三方可能传推送码，统一定位到真实通道后再点播
        GbDeviceChannel channel = catalogService.resolveChannel(req.getDeviceId(), req.getChannelId());
        req.setDeviceId(channel.getDeviceId());
        req.setChannelId(channel.getChannelId());
        PlayResultResponse vo = playService.start(req);
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

    @Operation(summary = "停止点播")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAY_WRITE, name = OpenApiConstants.SCOPE_NAME_PLAY_WRITE)
    @PostMapping("/stop")
    public OpenApiResult<Void> stop(@RequestBody PlayStopRequest req, HttpServletRequest request) {
        playService.stop(req);
        String rid = OpenApiContextHolder.requestId(request);
        return OpenApiResult.success(null, rid);
    }
}
