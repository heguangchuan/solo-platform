package com.solo.openapi.controller;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.utils.SipUtils;
import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.exception.OpenApiException;
import com.solo.openapi.request.OpenPtzControlRequest;
import com.solo.openapi.service.OpenCatalogService;
import com.solo.openapi.support.OpenApiContextHolder;
import com.solo.openapi.web.OpenApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API 云台控制。三条内部接口收敛为一个入口，指令走 request body。
 */
@Tag(name = "开放API-云台控制", description = "方向、变倍、停止")
@RestController
@RequestMapping("/open-api/v1/ptz")
@RequiredArgsConstructor
public class OpenPtzController {

    private final GbDeviceService deviceService;
    private final SipCommander sipCommander;
    private final OpenCatalogService catalogService;

    @Operation(summary = "云台控制")
    @RequireScope(value = OpenApiConstants.SCOPE_PTZ_CTRL, name = "云台控制")
    @PostMapping("/control")
    public OpenApiResult<Void> control(@Valid @RequestBody OpenPtzControlRequest req, HttpServletRequest request) {
        // 三方可能传推送码，统一定位到真实通道后再下发 SIP 指令
        GbDeviceChannel channel = catalogService.resolveChannel(req.getDeviceId(), req.getChannelId());
        req.setDeviceId(channel.getDeviceId());
        req.setChannelId(channel.getChannelId());

        int speed = req.getSpeed() == null ? 100 : req.getSpeed();
        int zoomSpeed = req.getZoomSpeed() == null ? 5 : req.getZoomSpeed();
        if (speed < 0 || speed > 255) {
            throw new OpenApiException(OpenApiErrorCode.PARAM_INVALID, "speed 取值范围 0-255");
        }
        if (zoomSpeed < 0 || zoomSpeed > 15) {
            throw new OpenApiException(OpenApiErrorCode.PARAM_INVALID, "zoomSpeed 取值范围 0-15");
        }

        GbDevice device = deviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, req.getDeviceId())
                .one();
        if (device == null) {
            throw new OpenApiException(OpenApiErrorCode.DEVICE_NOT_FOUND, "设备不存在: " + req.getDeviceId());
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new OpenApiException(OpenApiErrorCode.DEVICE_OFFLINE, "设备离线，无法控制");
        }

        int leftRight = 0;
        int upDown = 0;
        int inOut = 0;
        switch (req.getCommand().toLowerCase()) {
            case "left" -> leftRight = 1;
            case "right" -> leftRight = 2;
            case "up" -> upDown = 1;
            case "down" -> upDown = 2;
            case "upleft" -> {
                leftRight = 1;
                upDown = 1;
            }
            case "upright" -> {
                leftRight = 2;
                upDown = 1;
            }
            case "downleft" -> {
                leftRight = 1;
                upDown = 2;
            }
            case "downright" -> {
                leftRight = 2;
                upDown = 2;
            }
            case "zoomin" -> inOut = 2;
            case "zoomout" -> inOut = 1;
            case "stop" -> {
                speed = 0;
                zoomSpeed = 0;
            }
            default -> throw new OpenApiException(OpenApiErrorCode.PARAM_INVALID,
                    "不支持的云台指令: " + req.getCommand());
        }

        String ptzCmd = SipUtils.buildPtzCmd(leftRight, upDown, inOut, speed, zoomSpeed);
        sipCommander.sendPtzCmd(device, req.getChannelId(), ptzCmd);
        return OpenApiResult.success(null, OpenApiContextHolder.requestId(request));
    }
}
