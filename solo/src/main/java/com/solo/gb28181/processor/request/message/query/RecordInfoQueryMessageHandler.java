package com.solo.gb28181.processor.request.message.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.support.UpperRecordQueryHolder;
import com.solo.gb28181.support.UpperRecordQueryHolder.UpperQueryContext;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Response;
import java.util.List;

// 上级来查录像，先回 200，再换 inner SN 转给设备；设备回包后由 holder 换回 outer SN。
// 查不到设备、字段不全、发送失败或超时都回空列表，别让上级一直等。多级级联没做。
@Slf4j
@Component
public class RecordInfoQueryMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private static final String LOG_TAG = "[级联录像查询]";

    @Autowired
    private GbDeviceMapper deviceMapper;

    // @Lazy 用来拆 SipLayer -> 消息处理器 -> SipCommander 的循环依赖。
    @Autowired
    @Lazy
    private SipCommander sipCommander;

    @Autowired
    private UpperRecordQueryHolder upperRecordQueryHolder;

    @Autowired
    private PlatformCommander platformCommander;

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

        try {
            responseAck(request, Response.OK);
        } catch (Exception e) {
            log.warn("{} 回 200 失败 platform={} SN={}: {}",
                    LOG_TAG, platform.getServerGbId(), sn, e.getMessage());
            return;
        }

        if (deviceId == null || deviceId.isBlank() || startTime == null || endTime == null) {
            log.warn("{} 收到上级查询但字段不全 platform={} SN={} deviceId={} startTime={} endTime={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId, startTime, endTime);
            platformCommander.sendRecordInfoResponse(platform, sn, deviceId == null ? "" : deviceId, List.of());
            return;
        }

        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device == null) {
            log.warn("{} 设备未登记，无法转发 platform={} SN={} deviceId={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId);
            platformCommander.sendRecordInfoResponse(platform, sn, deviceId, List.of());
            return;
        }

        int snInner = SipCommander.nextSn();
        upperRecordQueryHolder.register(snInner,
                new UpperQueryContext(platform, sn, deviceId));
        try {
            sipCommander.queryRecordInfo(device, deviceId, snInner, startTime, endTime);
            log.info("{} 转发给设备 platform={} SN={} deviceId={} innerSN={} {} ~ {}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId, snInner, startTime, endTime);
        } catch (Exception e) {
            // 发送失败：取消 holder 登记，主动回空避免上级卡等
            upperRecordQueryHolder.cancel(snInner);
            log.warn("{} 转发失败 platform={} SN={} innerSN={}: {}",
                    LOG_TAG, platform.getServerGbId(), sn, snInner, e.getMessage());
            platformCommander.sendRecordInfoResponse(platform, sn, deviceId, List.of());
        }
    }
}
