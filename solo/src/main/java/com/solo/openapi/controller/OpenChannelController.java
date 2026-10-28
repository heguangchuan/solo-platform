package com.solo.openapi.controller;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.service.OpenCatalogService;
import com.solo.openapi.support.OpenApiContextHolder;
import com.solo.openapi.web.OpenApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API 通道目录。通道编码仅在设备内唯一，跨设备重码时需带 deviceId 消歧。
 */
@Tag(name = "开放API-设备目录", description = "按国标编码查询设备与通道详情")
@RestController
@RequestMapping("/open-api/v1/channel")
@RequiredArgsConstructor
public class OpenChannelController {

    private final OpenCatalogService catalogService;

    @Operation(summary = "通道详情")
    @RequireScope(value = OpenApiConstants.SCOPE_DEVICE_READ, name = "设备目录只读")
    @GetMapping("/{channelId}")
    public OpenApiResult<GbDeviceChannel> channel(@PathVariable String channelId,
                                                  @RequestParam(required = false) String deviceId,
                                                  HttpServletRequest request) {
        return OpenApiResult.success(catalogService.channelDetail(channelId, deviceId),
                OpenApiContextHolder.requestId(request));
    }
}
