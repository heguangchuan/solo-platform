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

/**
 * 上级平台 RecordInfo 查询处理（ADR-031 已落地）。
 * <p>本平台作为下级挂上级时，上级发来
 * {@code <Query><CmdType>RecordInfo</CmdType>...</Query>} 索要某通道某时段录像列表。
 * 本平台不存录像，必须主动向下级设备转发同样的 {@code Query/RecordInfo}：</p>
 *
 * <h3>转发路径</h3>
 * <ol>
 *   <li>立刻回 200 OK 给上级（释放 SIP 事务）</li>
 *   <li>查 {@link GbDevice}，命中则走主动转发路径（生成 inner SN →
 *       登记 {@link UpperRecordQueryHolder} → {@link SipCommander#queryRecordInfo} 转发给设备）</li>
 *   <li>设备未登记则立刻给上级回 {@code SumNum=0} 空响应（不挂起）</li>
 *   <li>设备响应回包在 {@code RecordInfoResponseMessageHandler} 里命中 holder，
 *       改 outer SN 转给上级（详见 {@link UpperRecordQueryHolder}）</li>
 *   <li>30s 内设备不响应，holder sweep 主动给上级回空响应，避免上级无限等</li>
 * </ol>
 *
 * <h3>限制</h3>
 * <ul>
 *   <li>只支持直接登记的 {@link GbDevice}；上级的 {@code DeviceID} 命中子平台（{@code GbPlatform}）
 *       时不做多级级联转发——S6.b 后续迭代</li>
 *   <li>假设上级的 {@code DeviceID} 是设备级 ID（与 {@code GbPlaybackServiceImpl.queryRecordInfo}
 *       的 UI 行为对齐），如果是通道级 ID 走 lookup fallback——见下方 fallback 逻辑</li>
 * </ul>
 */
@Slf4j
@Component
public class RecordInfoQueryMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private static final String LOG_TAG = "[级联录像查询]";

    @Autowired
    private GbDeviceMapper deviceMapper;

    /**
     * 用 {@code @Lazy} 避免循环依赖（ADR-015）：
     * <pre>
     *   RecordInfoQueryMessageHandler → SipCommander → SipLayer → SipProcessorObserver
     *   → List&lt;SipRequestProcessor&gt; → MessageRequestProcessor → List&lt;MessageHandler&gt;
     *   → RecordInfoQueryMessageHandler
     * </pre>
     * 设备应答超时 30s 才走 holder sweep，期间不调用本字段，{@code @Lazy} 零副作用。
     */
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
            // 不登记 holder、不转发设备；直接给上级回空（避免静默丢）
            platformCommander.sendRecordInfoResponse(platform, sn, deviceId == null ? "" : deviceId, List.of());
            return;
        }

        // 按设备 ID 查本平台登记表。命中则转发；未命中回空
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device == null) {
            log.warn("{} 设备未登记，无法转发 platform={} SN={} deviceId={}",
                    LOG_TAG, platform.getServerGbId(), sn, deviceId);
            platformCommander.sendRecordInfoResponse(platform, sn, deviceId, List.of());
            return;
        }

        // 生成 inner SN，登记 holder，再转发给设备
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
