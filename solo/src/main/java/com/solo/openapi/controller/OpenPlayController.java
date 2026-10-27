package com.solo.openapi.controller;

import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.GbPlayService;
import com.solo.openapi.auth.RequireScope;
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

    @Operation(summary = "开始点播")
    @RequireScope("gb:play:write")
    @PostMapping("/start")
    public OpenApiResult<PlayResultResponse> start(@RequestBody PlayStartRequest req, HttpServletRequest request) {
        String rid = (String) request.getAttribute(com.solo.openapi.constant.OpenApiConstants.HEADER_REQUEST_ID);
        return OpenApiResult.success(playService.start(req), rid);
    }

    @Operation(summary = "停止点播")
    @RequireScope("gb:play:write")
    @PostMapping("/stop")
    public OpenApiResult<Void> stop(@RequestBody PlayStopRequest req, HttpServletRequest request) {
        playService.stop(req);
        String rid = (String) request.getAttribute(com.solo.openapi.constant.OpenApiConstants.HEADER_REQUEST_ID);
        return OpenApiResult.success(null, rid);
    }
}
