package com.solo.gb28181.processor.request.message.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.entity.GbPlatformShare;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbPlatformShareMapper;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Response;


@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStatusQueryMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private static final String LOG_TAG = "[级联设备状态]";

    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbPlatformShareMapper shareMapper;
    private final PlatformCommander platformCommander;
    private final Gb28181Properties properties;

    @Override
    public String getRootType() { return "Query"; }

    @Override
    public String getCmdType() { return "DeviceStatus"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        // 下级设备不会向本平台发 DeviceStatus Query（设备不会反过来查本平台），忽略即可。
        // 200 OK 已由 MessageRequestProcessor 在分发前统一回复。
    }

    @Override
    public void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        SIPRequest request = (SIPRequest) event.getRequest();
        String sn = XmlUtils.childText(root, "SN");
        String deviceId = XmlUtils.childText(root, "DeviceID");

        try {
            responseAck(request, Response.OK);
        } catch (Exception e) {
            log.warn("{} 回 200 失败 platform={} SN={}: {}",
                    LOG_TAG, platform.getServerGbId(), sn, e.getMessage());
            return;
        }

        if (deviceId == null || deviceId.isBlank()) {
            log.warn("{} 收到上级查询但 DeviceID 为空 platform={} SN={}",
                    LOG_TAG, platform.getServerGbId(), sn);
            platformCommander.sendDeviceStatusError(platform, "", sn, "DeviceID 为空");
            return;
        }

        // 1) 设备维度
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device != null) {
            log.info("{} 设备命中下发 platform={} SN={} deviceId={} status={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId, device.getStatus());
            platformCommander.sendDeviceStatusResponse(platform, sn, device);
            return;
        }

        // 2) 本级平台自身
        if (deviceId.equals(properties.getId())) {
            log.info("{} 本级平台状态下发 platform={} SN={} deviceId={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId);
            platformCommander.sendDeviceStatusResponse(platform, sn, deviceId, "ON");
            return;
        }

        // 3) 通道维度：国标 DeviceStatus 按通道，上级常传通道国标码
        GbDeviceChannel channel = resolveChannel(platform, deviceId);
        if (channel != null) {
            log.info("{} 通道命中下发 platform={} SN={} channelId={} status={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId, channel.getStatus());
            platformCommander.sendDeviceStatusResponse(platform, sn, deviceId, channel.getStatus());
            return;
        }

        log.warn("{} 设备/通道未登记 platform={} SN={} deviceId={}",
                LOG_TAG, platform.getServerGbId(), sn, deviceId);
        platformCommander.sendDeviceStatusError(platform, deviceId, sn, "设备/通道未在本平台登记");
    }

    private GbDeviceChannel resolveChannel(GbParentPlatform platform, String deviceId) {
        if (platform.getId() == null) {
            return null;
        }
        Long platformId = platform.getId();

        // 1) channel_id 直查 + 共享校验
        GbDeviceChannel channel = channelMapper.selectOne(
                new LambdaQueryWrapper<GbDeviceChannel>().eq(GbDeviceChannel::getChannelId, deviceId));
        if (channel != null && channel.getId() != null) {
            GbPlatformShare share = shareMapper.selectOne(new LambdaQueryWrapper<GbPlatformShare>()
                    .eq(GbPlatformShare::getPlatformId, platformId)
                    .eq(GbPlatformShare::getResourceId, channel.getId())
                    .last("LIMIT 1"));
            if (share != null) {
                return channel;
            }
        }

        // 2) exposed_code 反查（撞码场景：上级拿到的是暴露码而非原始 channel_id）
        GbPlatformShare share = shareMapper.selectOne(new LambdaQueryWrapper<GbPlatformShare>()
                .eq(GbPlatformShare::getPlatformId, platformId)
                .eq(GbPlatformShare::getExposedCode, deviceId)
                .last("LIMIT 1"));
        if (share == null || share.getResourceId() == null) {
            return null;
        }
        return channelMapper.selectById(share.getResourceId());
    }
}
