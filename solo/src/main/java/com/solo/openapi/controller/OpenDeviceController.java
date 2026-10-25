package com.solo.openapi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.common.PageResult;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.request.GbDevicePageRequest;
import com.solo.gb28181.response.GbDeviceChannelResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.common.ServiceException;
import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.controller.vo.OpenChannelVO;
import com.solo.openapi.controller.vo.OpenDeviceVO;
import com.solo.openapi.exception.OpenApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 一期开放接口：设备/通道查询。只做只读，不暴露内部主键 id。
 */
@Tag(name = "开放API-设备查询", description = "第三方设备/通道查询")
@RestController
@RequestMapping("/open-api/v1/devices")
@RequiredArgsConstructor
public class OpenDeviceController {

    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;

    @Operation(summary = "分页查询设备")
    @PostMapping("/page")
    @RequireScope(OpenApiConstants.SCOPE_DEVICE_READ)
    public PageResult<OpenDeviceVO> pageDevices(@RequestBody GbDevicePageRequest req) {
        Page<GbDevice> page = deviceService.pageQuery(req);
        List<OpenDeviceVO> rows = page.getRecords().stream().map(this::toDeviceVO).toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    @Operation(summary = "按国标 ID 查询设备详情")
    @GetMapping("/{deviceId}")
    @RequireScope(OpenApiConstants.SCOPE_DEVICE_READ)
    public OpenDeviceVO deviceDetail(@PathVariable String deviceId) {
        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device == null) {
            throw new OpenApiException(OpenApiErrorCode.DEVICE_NOT_FOUND, "设备不存在: " + deviceId);
        }
        return toDeviceVO(device);
    }

    @Operation(summary = "分页查询某台设备的通道")
    @PostMapping("/channels/page")
    @RequireScope(OpenApiConstants.SCOPE_DEVICE_READ)
    public PageResult<OpenChannelVO> pageChannels(@RequestBody GbDeviceChannelPageRequest req) {
        if (!StringUtils.hasText(req.getDeviceId())) {
            throw new ServiceException("deviceId 不能为空");
        }
        Page<GbDeviceChannelResponse> page = channelService.pageQuery(req);
        List<OpenChannelVO> rows = page.getRecords().stream().map(this::toChannelVO).toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    private OpenDeviceVO toDeviceVO(GbDevice d) {
        // 展示名跟内部保持一致：自定义名优先，没填就回退设备上报名
        String displayName = d.getName() != null ? d.getName() : d.getReportName();
        return new OpenDeviceVO(d.getDeviceId(), displayName, d.getManufacturer(), d.getModel(),
                d.getStatus(), d.getTransport(), d.getIp(), d.getPort(),
                d.getRegisterTime(), d.getKeepaliveTime());
    }

    private OpenChannelVO toChannelVO(GbDeviceChannel c) {
        return new OpenChannelVO(c.getChannelId(), c.getDeviceId(), c.getName(), c.getStatus(),
                c.getParental(), c.getParentId(), c.getManufacturer(), c.getModel(),
                c.getPtzType(), c.getLongitude(), c.getLatitude());
    }
}
