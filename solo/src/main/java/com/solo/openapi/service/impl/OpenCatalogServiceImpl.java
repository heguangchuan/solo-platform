package com.solo.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPlatformShare;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbPlatformShareMapper;
import com.solo.openapi.constant.OpenApiErrorCode;
import com.solo.openapi.exception.OpenApiException;
import com.solo.openapi.service.OpenCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OpenCatalogServiceImpl implements OpenCatalogService {

    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbPlatformShareMapper shareMapper;

    @Override
    public GbDevice deviceDetail(String deviceId) {
        GbDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<GbDevice>()
                .eq(GbDevice::getDeviceId, deviceId));
        if (device == null) {
            throw new OpenApiException(OpenApiErrorCode.DEVICE_NOT_FOUND, "设备不存在: " + deviceId);
        }
        return device;
    }

    @Override
    public GbDeviceChannel channelDetail(String channelId, String deviceId) {
        GbDeviceChannel channel = resolveChannel(deviceId, channelId);
        // SIP 密码属于内部凭证，不对外返回
        channel.setPassword(null);
        return channel;
    }

    @Override
    public GbDeviceChannel resolveChannel(String deviceId, String channelId) {
        GbDeviceChannel channel = findRealChannel(deviceId, channelId);
        if (channel == null) {
            channel = findSharedChannel(channelId);
        }
        if (channel == null) {
            throw new OpenApiException(OpenApiErrorCode.RESOURCE_NOT_FOUND, "通道不存在: " + channelId);
        }
        return channel;
    }

    private GbDeviceChannel findRealChannel(String deviceId, String channelId) {
        if (StringUtils.hasText(deviceId)) {
            return channelMapper.selectOne(new LambdaQueryWrapper<GbDeviceChannel>()
                    .eq(GbDeviceChannel::getChannelId, channelId)
                    .eq(GbDeviceChannel::getDeviceId, deviceId));
        }
        List<GbDeviceChannel> channels = channelMapper.selectList(new LambdaQueryWrapper<GbDeviceChannel>()
                .eq(GbDeviceChannel::getChannelId, channelId));
        if (channels.size() > 1) {
            throw new OpenApiException(OpenApiErrorCode.PARAM_INVALID,
                    "通道编码在多个设备下存在，请携带 deviceId 参数: " + channelId);
        }
        return channels.isEmpty() ? null : channels.getFirst();
    }

    /**
     * 推送码只在共享撞码时分配，且不收回，同一通道跨多个上级复用同一码，因此要按 resourceId 去重。
     * 设备按推送码定位时，以推送码为准，忽略请求里的 deviceId。
     */
    private GbDeviceChannel findSharedChannel(String channelId) {
        List<Long> resourceIds = shareMapper.selectList(new LambdaQueryWrapper<GbPlatformShare>()
                        .eq(GbPlatformShare::getExposedCode, channelId)
                        .select(GbPlatformShare::getResourceId))
                .stream()
                .map(GbPlatformShare::getResourceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (resourceIds.size() > 1) {
            throw new OpenApiException(OpenApiErrorCode.PARAM_INVALID,
                    "推送码对应多个通道，无法定位: " + channelId);
        }
        if (resourceIds.isEmpty()) {
            return null;
        }
        GbDeviceChannel channel = channelMapper.selectById(resourceIds.getFirst());
        if (channel != null) {
            channel.setExposedCode(channelId);
        }
        return channel;
    }
}
