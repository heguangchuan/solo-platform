package com.solo.gb28181.processor.request.message.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbDeviceMapper;
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
 * 上级平台 DeviceInfo 查询处理（ADR-031 已落地）。
 * <p>本平台作为下级挂上级时，上级发来
 * {@code <Query><CmdType>DeviceInfo</CmdType>...</Query>} 索要下级某设备的厂商/型号/固件。
 * 本 handler：</p>
 * <ol>
 *   <li>立刻回 200 OK（SIP 事务层）</li>
 *   <li>查 {@link GbDevice} 表，命中回 {@code Response/DeviceInfo} 含完整厂商信息</li>
 *   <li>未命中回 {@code Result=ERROR} 兜底（避免上级无限重试）</li>
 * </ol>
 *
 * <p>与 {@code DeviceInfoResponseMessageHandler}（{@code Response/DeviceInfo}，处理"本平台向上级查设备、
 * 设备回包"）方向相反——本 handler 走的是 <b>上级 → 本平台（作为下级）</b>。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceInfoQueryMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private static final String LOG_TAG = "[级联设备信息]";

    private final GbDeviceMapper deviceMapper;
    private final PlatformCommander platformCommander;

    @Override
    public String getRootType() { return "Query"; }

    @Override
    public String getCmdType() { return "DeviceInfo"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        // 下级设备不会向本平台发 DeviceInfo Query（设备不会反过来查本平台），忽略即可。
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
            platformCommander.sendDeviceInfoError(platform, "", sn, "DeviceID 为空");
            return;
        }

        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device != null) {
            log.info("{} 命中下发 platform={} SN={} deviceId={} manufacturer={} model={} firmware={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId,
                    device.getManufacturer(), device.getModel(), device.getFirmware());
            platformCommander.sendDeviceInfoResponse(platform, sn, device);
        } else {
            log.warn("{} 设备未登记 platform={} SN={} deviceId={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId);
            platformCommander.sendDeviceInfoError(platform, deviceId, sn, "设备未在本平台登记");
        }
    }
}
