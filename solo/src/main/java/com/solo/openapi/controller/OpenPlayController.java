package com.solo.openapi.controller;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.GbPlayService;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;
import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.auth.OpenAppContext;
import com.solo.openapi.constant.OpenApiConstants;
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

/**
 * 开放 API 实时点播接口。三方通过 appKey/appSecret 签名调用，
 * 需具备 gb:play:write scope。
 * 复用 {@link GbPlayService}，入参出参与内部管理端一致。
 */
@Tag(name = "开放API-实时点播", description = "三方调用实时点播")
@RestController
@RequestMapping("/open-api/v1/play")
@RequiredArgsConstructor
public class OpenPlayController {

    private final GbPlayService playService;
    private final OpenCatalogService catalogService;
    private final ViewTokenSigner viewTokenSigner;

    @Operation(summary = "开始点播")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAY_WRITE, name = "实时点播控制")
    @PostMapping("/start")
    public OpenApiResult<PlayResultResponse> start(@RequestBody PlayStartRequest req, HttpServletRequest request) {
        // 三方可能传推送码，统一定位到真实通道后再点播
        GbDeviceChannel channel = catalogService.resolveChannel(req.getDeviceId(), req.getChannelId());
        req.setDeviceId(channel.getDeviceId());
        req.setChannelId(channel.getChannelId());
        PlayResultResponse vo = playService.start(req);
        signWithOpenApp(vo, request);
        return OpenApiResult.success(vo, OpenApiContextHolder.requestId(request));
    }

    /** 开放 API 请求不走平台用户体系，用应用身份给播放地址签观看令牌 */
    private void signWithOpenApp(PlayResultResponse vo, HttpServletRequest request) {
        OpenAppContext ctx = OpenApiContextHolder.get(request);
        if (ctx == null) {
            return;
        }
        ViewActor actor = ViewActor.openApp(ctx.getAppId(), ctx.getAppName());
        String sid = vo.getStreamId();
        vo.setFlvUrl(viewTokenSigner.appendToken(vo.getFlvUrl(), actor, sid));
        vo.setWsFlvUrl(viewTokenSigner.appendToken(vo.getWsFlvUrl(), actor, sid));
        vo.setHlsUrl(viewTokenSigner.appendToken(vo.getHlsUrl(), actor, sid));
        vo.setRtcUrl(viewTokenSigner.appendToken(vo.getRtcUrl(), actor, sid));
    }

    @Operation(summary = "停止点播")
    @RequireScope(value = OpenApiConstants.SCOPE_PLAY_WRITE, name = "实时点播控制")
    @PostMapping("/stop")
    public OpenApiResult<Void> stop(@RequestBody PlayStopRequest req, HttpServletRequest request) {
        playService.stop(req);
        String rid = OpenApiContextHolder.requestId(request);
        return OpenApiResult.success(null, rid);
    }
}
