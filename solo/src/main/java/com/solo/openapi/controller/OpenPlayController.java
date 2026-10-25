package com.solo.openapi.controller;

import com.solo.common.ServiceException;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlayStopRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.GbPlayService;
import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.controller.vo.OpenPlayResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一期开放接口：实时点播开始/停止。直接复用内部 GbPlayService，入参出参换成开放侧模型。
 */
@Tag(name = "开放API-实时点播", description = "第三方实时点播控制")
@RestController
@RequestMapping("/open-api/v1/plays")
@RequiredArgsConstructor
public class OpenPlayController {

    private final GbPlayService playService;

    @Operation(summary = "开始实时点播")
    @PostMapping("/start")
    @RequireScope(OpenApiConstants.SCOPE_PLAY_CTRL)
    public OpenPlayResultVO start(@RequestBody PlayStartRequest req) {
        if (!StringUtils.hasText(req.getDeviceId()) || !StringUtils.hasText(req.getChannelId())) {
            throw new ServiceException("deviceId、channelId 不能为空");
        }
        PlayResultResponse result = playService.start(req);
        return new OpenPlayResultVO(result.getStreamId(), result.getTransport(),
                result.getFlvUrl(), result.getWsFlvUrl(), result.getHlsUrl(), result.getRtcUrl());
    }

    @Operation(summary = "停止实时点播")
    @PostMapping("/stop")
    @RequireScope(OpenApiConstants.SCOPE_PLAY_CTRL)
    public Void stop(@RequestBody PlayStopRequest req) {
        if (!StringUtils.hasText(req.getStreamId())) {
            throw new ServiceException("streamId 不能为空");
        }
        playService.stop(req);
        return null;
    }
}
