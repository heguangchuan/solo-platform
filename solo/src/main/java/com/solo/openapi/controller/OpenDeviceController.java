package com.solo.openapi.controller;

import com.solo.gb28181.entity.GbDevice;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API 设备目录。三方已自行维护国标编码，这里只按编码查详情，不提供分页与增删改。
 */
@Tag(name = "开放API-设备目录", description = "按国标编码查询设备与通道详情")
@RestController
@RequestMapping("/open-api/v1/device")
@RequiredArgsConstructor
public class OpenDeviceController {

    private final GbDeviceService deviceService;

    @Operation(summary = "设备详情")
    @RequireScope(value = OpenApiConstants.SCOPE_DEVICE_READ, name = "设备目录只读")
    @GetMapping("/{deviceId}")
    public OpenApiResult<GbDevice> device(@PathVariable String deviceId, HttpServletRequest request) {
        GbDevice device = deviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, deviceId)
                .one();
        if (device == null) {
            throw new OpenApiException(OpenApiErrorCode.DEVICE_NOT_FOUND, "设备不存在: " + deviceId);
        }
        return OpenApiResult.success(device, requestId(request));
    }

    private static String requestId(HttpServletRequest request) {
        Object rid = request.getAttribute(OpenApiConstants.HEADER_REQUEST_ID);
        return rid != null ? rid.toString() : request.getHeader(OpenApiConstants.HEADER_REQUEST_ID);
    }
}
