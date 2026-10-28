package com.solo.openapi.service;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;

/**
 * 开放 API 设备目录查询。只按国标编码查详情，不做分页与增删改。
 */
public interface OpenCatalogService {

    GbDevice deviceDetail(String deviceId);

    GbDeviceChannel channelDetail(String channelId, String deviceId);

    /**
     * 按真实通道码或级联推送码定位通道。
     * <p>先按 {@code channel_id}（带 deviceId 时精确匹配）查；未命中再按
     * {@code gb_platform_share.exposed_code} 反查——三方从共享目录拿到的是推送码时走这条。</p>
     *
     * @return 命中通道，返回体 {@code channelId} 一定是真实通道码，便于后续点播/回放/云台直接下发
     * @throws com.solo.openapi.exception.OpenApiException 通道不存在（40002）或编码歧义（40001）
     */
    GbDeviceChannel resolveChannel(String deviceId, String channelId);
}
