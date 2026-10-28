package com.solo.openapi.controller;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.exception.OpenApiException;
import com.solo.openapi.web.OpenApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放 API 设备目录。三方已自行维护国标编码，这里只按编码查详情，不提供分页与增删改。
 */
@Tag(name = "开放API-设备目录", description = "按国标编码查询设备与通道详情")
@RestController
@RequestMapping("/open-api/v1")
@RequiredArgsConstructor
public class OpenDeviceController {

    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;

    @Operation(summary = "设备详情")
    @RequireScope(value = OpenApiConstants.SCOPE_DEVICE_READ, name = "设备目录只读")
    @GetMapping("/device/{deviceId}")
    public OpenApiResult<GbDevice> device(@PathVariable String deviceId, HttpServletRequest request) {
        GbDevice device = deviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, deviceId)
                .one();
        if (device == null) {
            throw new OpenApiException(OpenApiErrorCode.DEVICE_NOT_FOUND, "设备不存在: " + deviceId);
        }
        return OpenApiResult.success(device, requestId(request));
    }

    @Operation(summary = "通道详情")
    @RequireScope(value = OpenApiConstants.SCOPE_DEVICE_READ, name = "设备目录只读")
    @GetMapping("/channel/{channelId}")
    public OpenApiResult<GbDeviceChannel> channel(@PathVariable String channelId,
                                                  @RequestParam(required = false) String deviceId,
                                                  HttpServletRequest request) {
        List<GbDeviceChannel> channels = channelService.lambdaQuery()
                .eq(GbDeviceChannel::getChannelId, channelId)
                .eq(StringUtils.hasText(deviceId), GbDeviceChannel::getDeviceId, deviceId)
                .list();
        if (channels.isEmpty()) {
            throw new OpenApiException(OpenApiErrorCode.RESOURCE_NOT_FOUND, "通道不存在: " + channelId);
        }
        if (channels.size() > 1) {
            throw new OpenApiException(OpenApiErrorCode.PARAM_INVALID,
                    "通道编码在多个设备下存在，请携带 deviceId 参数: " + channelId);
        }
        GbDeviceChannel channel = channels.getFirst();
        // SIP 密码属于内部凭证，不对外返回
        channel.setPassword(null);
        return OpenApiResult.success(channel, requestId(request));
    }

    private static String requestId(HttpServletRequest request) {
        Object rid = request.getAttribute(OpenApiConstants.HEADER_REQUEST_ID);
        return rid != null ? rid.toString() : request.getHeader(OpenApiConstants.HEADER_REQUEST_ID);
    }
}
