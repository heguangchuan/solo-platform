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

/**
 * <p>本平台作为下级挂上级时，上级发来
 * {@code <Query><CmdType>DeviceStatus</CmdType>...</Query>} 索要下级某设备/通道的在线/健康状态。
 * 本 handler：</p>
 * <ol>
 *   <li>立刻回 200 OK（SIP 事务层）</li>
 *   <li>设备维度：查 {@link GbDevice} 表，按 {@code status}（0/1/2）映射 Online/Status 回包</li>
 *   <li>本级平台自身（deviceId == 本平台 ID）按在线、健康回包</li>
 *   <li>通道维度：国标 DeviceStatus 本就是按通道的，上级常传通道国标码来查——先按
 *       {@code channel_id} 查 {@link GbDeviceChannel}；查不到再按本平台 {@code gb_platform_share}
 *       的 exposedCode 反查（撞码场景上级拿的是暴露码而非原始 channel_id），取回通道后按
 *       通道 {@code status}（{@code ON}/{@code OFF}/{@code UNREGISTERED}）映射回包</li>
 *   <li>设备/通道都查不到才回 {@code Result=ERROR} 兜底（避免上级无限重试）</li>
 * </ol>
 *
 * <p>与 {@link DeviceInfoQueryMessageHandler} 方向相反一致——本 handler 走的是
 * <b>上级 → 本平台（作为下级）</b>。</p>
 */
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

    /**
     * 按上级传来的 DeviceID 反查已共享给该上级的通道。
     * 先按 {@code channel_id} 直查 + 共享校验；命中不到再按
     * {@code gb_platform_share.exposed_code} 反查（撞码场景）。
     * 两条路径都走 share 表校验，避免未共享通道被上级探测到。
     *
     * @return 命中且已共享返回通道，否则 null
     */
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
