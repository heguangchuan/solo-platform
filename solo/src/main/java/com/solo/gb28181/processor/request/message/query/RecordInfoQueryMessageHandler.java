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
 * 上级平台 RecordInfo 查询处理（ADR-031）。
 * <p>本平台作为下级挂上级时，上级发来
 * {@code <Query><CmdType>RecordInfo</CmdType>...</Query>} 索要某设备某时段录像列表。
 * 报文 body 除通用 {@code SN}/{@code DeviceID} 外，可能还带 {@code StartTime}/{@code EndTime}/{@code Type}/{@code SecNum}。
 * 与 {@code RecordInfoResponseMessageHandler}（{@code Response/RecordInfo}，处理"本平台向设备查录像、设备回包"）
 * 方向相反——本 handler 走的是 <b>上级 → 本平台（作为下级）</b>。</p>
 *
 * <h3>当前实现范围（ADR-031）</h3>
 * <ul>
 *   <li>识别为上级平台 Query 后，立刻回 200 OK 释放 SIP 事务</li>
 *   <li>打 INFO 留痕：{@code [级联录像查询] 收到上级查询 platform=... SN=... deviceId=... startTime=... endTime=... type=...}</li>
 *   <li><b>不回业务 body</b>——按当前 Sprint 范围，200 + 日志即可，回应 RecordInfo XML 留作后续 ADR</li>
 * </ul>
 *
 * <h3>不做</h3>
 * <ul>
 *   <li>❌ 回 {@code <Response><CmdType>RecordInfo>...</CmdType></Response>} XML body
 *       ——Sprint 待 S3 回放/录像下载链路完善后再补</li>
 *   <li>❌ 录像分片合并——当前 solo 本身不存录像，无数据可推</li>
 * </ul>
 *
 * @see com.solo.gb28181.processor.request.message.response.RecordInfoResponseMessageHandler 设备响应方向
 */
@Slf4j
@Component
public class RecordInfoQueryMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private static final String LOG_TAG = "[级联录像查询]";

    @Override
    public String getRootType() { return "Query"; }

    @Override
    public String getCmdType() { return "RecordInfo"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        // 下级设备不会向本平台发 RecordInfo Query，忽略。
        // 200 OK 已由 MessageRequestProcessor 在分发前统一回复。
    }

    @Override
    public void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        SIPRequest request = (SIPRequest) event.getRequest();
        String sn = XmlUtils.childText(root, "SN");
        String deviceId = XmlUtils.childText(root, "DeviceID");
        String startTime = XmlUtils.childText(root, "StartTime");
        String endTime = XmlUtils.childText(root, "EndTime");
        String type = XmlUtils.childText(root, "Type");

        try {
            responseAck(request, Response.OK);
        } catch (Exception e) {
            log.warn("{} 回 200 失败 platform={} SN={}: {}",
                    LOG_TAG, platform.getServerGbId(), sn, e.getMessage());
            return;
        }

        log.info("{} 收到上级查询 platform={} SN={} deviceId={} startTime={} endTime={} type={}",
                LOG_TAG, platform.getServerGbId(), sn, deviceId, startTime, endTime, type);
    }
}
