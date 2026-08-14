package com.solo.gb28181.processor.request.message.query;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Response;

/**
 * 上级平台 DeviceInfo 查询处理（ADR-031）。
 * <p>本平台作为下级挂上级时，上级发来
 * {@code <Query><CmdType>DeviceInfo</CmdType>...</Query>} 索要下级某设备的厂商/型号/固件/通道数。
 * 与 {@code DeviceInfoResponseMessageHandler}（{@code Response/DeviceInfo}，处理"本平台向上级查设备、
 * 设备回包"）方向相反——本 handler 走的是 <b>上级 → 本平台（作为下级）</b>。</p>
 *
 * <h3>当前实现范围（ADR-031）</h3>
 * <ul>
 *   <li>识别为上级平台 Query 后，立刻回 200 OK 释放 SIP 事务</li>
 *   <li>打 INFO 留痕：{@code [级联设备信息] 收到上级查询 platform=... SN=... deviceId=...}</li>
 *   <li><b>不回业务 body</b>——按当前 Sprint 范围，200 + 日志即可，回应 DeviceInfo XML 留作后续 ADR</li>
 * </ul>
 *
 * <h3>不做</h3>
 * <ul>
 *   <li>❌ 回 {@code <Response><CmdType>DeviceInfo>...</CmdType></Response>} XML body
 *       ——Sprint 待 S2.x 设备信息同步链路完成后再补</li>
 *   <li>❌ NOTIFY 推送——ADR-030</li>
 * </ul>
 *
 * @see com.solo.gb28181.processor.request.message.response.DeviceInfoResponseMessageHandler 设备响应方向
 */
@Slf4j
@Component
public class DeviceInfoQueryMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private static final String LOG_TAG = "[级联设备信息]";

    @Override
    public String getRootType() { return "Query"; }

    @Override
    public String getCmdType() { return "DeviceInfo"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        // 下级设备不会向本平台发 DeviceInfo Query（设备不会反过来查本平台），忽略。
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

        log.info("{} 收到上级查询 platform={} SN={} deviceId={}",
                LOG_TAG, platform.getServerGbId(), sn, deviceId);
    }
}
