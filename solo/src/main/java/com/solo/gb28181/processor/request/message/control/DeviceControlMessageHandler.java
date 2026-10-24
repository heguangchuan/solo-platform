package com.solo.gb28181.processor.request.message.control;

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
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Response;

/**
 * 上级平台发来的 DeviceControl：校验通道确实共享给了该上级，再把报文转给通道所属设备。
 * 报文体不解析、不改写业务字段，只换 SN 和 DeviceID（上级传的可能是撞码暴露码）。
 *
 * <p>PTZCmd/GuardCmd/AlarmCmd/RecordCmd/IFameCmd/TeleBoot/DragZoomIn/DragZoomOut/HomePosition
 * 都走透传。</p>
 *
 * <p>平台侧 MESSAGE 分发器不代回应答，由本 handler 自己回；通道没共享回 404，其余回 200。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceControlMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private static final String LOG_TAG = "[级联设备控制]";

    /** 支持透传的命令标签，一个报文命中第一个 */
    private static final String[] PASSTHROUGH_CMDS = {
            "PTZCmd", "GuardCmd", "AlarmCmd", "RecordCmd", "IFameCmd", "TeleBoot",
            "DragZoomIn", "DragZoomOut", "HomePosition"
    };

    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbPlatformShareMapper shareMapper;
    private final SipCommander sipCommander;
    private final Gb28181Properties properties;

    @Override
    public String getRootType() { return "Control"; }

    @Override
    public String getCmdType() { return "DeviceControl"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        // 下级设备不会向本平台发 DeviceControl，忽略即可。200 OK 已由 MessageRequestProcessor 统一回复。
    }

    @Override
    public void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        SIPRequest request = (SIPRequest) event.getRequest();
        String sn = XmlUtils.childText(root, "SN");
        String deviceId = XmlUtils.childText(root, "DeviceID");

        if (deviceId == null || deviceId.isBlank()) {
            log.warn("{} DeviceID 为空 platform={} SN={}", LOG_TAG, platform.getServerGbId(), sn);
            ackQuietly(request, Response.BAD_REQUEST);
            return;
        }

        // 识别命令标签
        String cmdTag = null;
        for (String tag : PASSTHROUGH_CMDS) {
            if (root.element(tag) != null) {
                cmdTag = tag;
                break;
            }
        }
        if (cmdTag == null) {
            log.warn("{} 无法识别的 DeviceControl 命令 platform={} SN={} deviceId={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId);
            ackQuietly(request, Response.OK);
            return;
        }

        // TeleBoot 目标是本平台自己：不能让上级把应用重启了
        if ("TeleBoot".equals(cmdTag) && deviceId.equals(properties.getId())) {
            log.warn("{} 拒绝上级远程启动本平台 platform={} SN={}", LOG_TAG, platform.getServerGbId(), sn);
            ackQuietly(request, Response.OK);
            return;
        }

        // 共享校验 + 通道反查（上级传来的可能是 channel_id 或撞码暴露码）
        GbDeviceChannel channel = resolveChannel(platform, deviceId);
        if (channel == null) {
            log.warn("{} 通道未共享给该上级或不存在 platform={} SN={} deviceId={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId);
            ackQuietly(request, Response.NOT_FOUND);
            return;
        }

        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, channel.getDeviceId()));
        if (device == null) {
            log.warn("{} 通道所属设备不存在 platform={} SN={} deviceId={} 所属设备={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId, channel.getDeviceId());
            ackQuietly(request, Response.NOT_FOUND);
            return;
        }

        // 转给真实设备，DeviceID 换成设备侧通道码、SN 重新分配
        sipCommander.sendDeviceControlPassthrough(device, channel.getChannelId(), root, cmdTag);
        log.info("{} 透传 {} platform={} SN={} 上级码={} → 设备={} 通道={}",
                LOG_TAG, cmdTag, platform.getServerGbId(), sn, deviceId,
                device.getDeviceId(), channel.getChannelId());
        ackQuietly(request, Response.OK);
    }

    /**
     * 按上级传来的 DeviceID 反查已共享给该上级的通道。
     * 先按 {@code channel_id} 直查 + 共享校验；命中不到再按
     * {@code gb_platform_share.exposed_code} 反查（撞码场景）。
     * 两条路径都走 share 表校验，避免未共享通道被上级操控。
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

    /** 回 SIP 状态码，应答异常只记日志 */
    private void ackQuietly(SIPRequest request, int statusCode) {
        try {
            responseAck(request, statusCode);
        } catch (Exception e) {
            log.warn("{} 回 SIP {} 失败: {}", LOG_TAG, statusCode, e.getMessage());
        }
    }
}
