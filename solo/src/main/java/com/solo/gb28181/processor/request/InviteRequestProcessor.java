package com.solo.gb28181.processor.request;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbBroadcastSession;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.service.impl.CascadeSendService;
import com.solo.gb28181.service.impl.GbBroadcastService;
import com.solo.gb28181.transmit.cmd.SdpBuilder;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 入站 INVITE 两类：上级点播本平台共享通道（异步级联取流后推给上级），
 * 以及设备收到广播通知后发来的反向音频 INVITE（回 200 后由 ZLM 推音频）。
 * 按 From-user 先查上级平台、再查在线设备，都不命中回 403。
 * 业务处理丢 gb28181BizExecutor 异步执行；级联和广播 service 用 @Lazy 注入破环。
 */
@Slf4j
@Component
public class InviteRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    @Autowired
    private GbParentPlatformService gbParentPlatformService;

    @Autowired
    private GbDeviceMapper deviceMapper;

    @Autowired
    @Lazy
    private CascadeSendService cascadeSendService;

    @Autowired
    @Lazy
    private GbBroadcastService gbBroadcastService;

    /** 字段名与 Bean 名一致，按名装配 gb28181-biz 专属线程池 */
    @Autowired
    private ThreadPoolExecutor gb28181BizExecutor;

    @Override
    public String getMethod() {
        return Request.INVITE;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SIPRequest request = (SIPRequest) event.getRequest();
        String fromUser = SipUtils.getUserIdFromFromHeader(request);

        byte[] raw = request.getRawContent();
        String sdp = raw == null ? null : new String(raw, java.nio.charset.StandardCharsets.UTF_8);

        // 识别上级平台（作为下级被上级点播）
        GbParentPlatform platform = gbParentPlatformService.lambdaQuery()
                .eq(GbParentPlatform::getServerGbId, fromUser)
                .eq(GbParentPlatform::getEnabled, 1)
                .one();
        if (platform != null) {
            handleUpstreamInvite(request, platform, sdp);
            return;
        }

        // 非上级平台：看是不是在线设备的语音广播反向 INVITE（m=audio）
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, fromUser));
        if (device != null && device.getStatus() != null && device.getStatus() == 1
                && sdp != null && SdpBuilder.extractAudioPort(sdp) > 0) {
            handleBroadcastInvite(request, device, sdp);
            return;
        }

        log.warn("[SIP INVITE] 来源既非已知上级平台也非在线广播设备 from={}，回 403", fromUser);
        responseAck(request, Response.FORBIDDEN, "unknown source");
    }

    /** 上级点播：解析通道 + SDP，100 后丢业务线程池走级联取流推流 */
    private void handleUpstreamInvite(SIPRequest request, GbParentPlatform platform, String upstreamSdp)
            throws SipException, ParseException {
        String channelId = resolveChannelId(request);
        if (channelId == null || channelId.isBlank()) {
            log.warn("[级联点播] 上级 {} INVITE 无法解析 channelId，回 400", platform.getServerGbId());
            responseAck(request, Response.BAD_REQUEST, "no channel id");
            return;
        }
        if (upstreamSdp == null || upstreamSdp.isBlank()) {
            log.warn("[级联点播] 上级 {} INVITE 无 SDP，回 400", platform.getServerGbId());
            responseAck(request, Response.BAD_REQUEST, "no sdp");
            return;
        }

        // 1) 立即回 100 Trying（SIP 线程内，不阻塞）
        responseAck(request, Response.TRYING);

        // 取流 + 推流 + 回 200 丢业务线程池
        // 上级喊话走 NOTIFY 后本平台主动反向 INVITE，不会走到这里
        String finalChannelId = channelId;
        gb28181BizExecutor.execute(() -> {
            try {
                cascadeSendService.onUpstreamInvite(request, platform, finalChannelId, upstreamSdp);
            } catch (Exception e) {
                log.error("[级联] 处理上级 INVITE 异常 platform={} channel={}",
                        platform.getServerGbId(), finalChannelId, e);
            }
        });
    }

    /**
     * 设备语音广播：通道码优先取 Subject/Request-URI。部分厂商（如海康部分机型）反向 INVITE 既不带
     * Subject、R-URI user 也为空，解析不到时按该设备唯一等待中的广播会话兜底；同时有多个会话在等
     * 则无法判定，回 400。100 后丢业务线程池。
     */
    private void handleBroadcastInvite(SIPRequest request, GbDevice device, String deviceSdp)
            throws SipException, ParseException {
        String channelId = resolveChannelId(request);
        if (channelId == null || channelId.isBlank()
                || gbBroadcastService.findWaitingSession(device.getDeviceId(), channelId) == null) {
            GbBroadcastSession waiting = gbBroadcastService.findSingleWaitingSession(device.getDeviceId());
            if (waiting == null) {
                log.warn("[语音播报] 设备 {} INVITE 通道码无法匹配等待会话，回 400", device.getDeviceId());
                responseAck(request, Response.BAD_REQUEST, "no pending broadcast");
                return;
            }
            channelId = waiting.getChannelId();
        }

        responseAck(request, Response.TRYING);

        String finalChannelId = channelId;
        // 兜底会话在上面的分支里，正常解析通道码时再按通道查一次拿模式，用于日志标题
        GbBroadcastSession matched = gbBroadcastService.findWaitingSession(device.getDeviceId(), finalChannelId);
        String logTag = matched != null && matched.isTalk() ? "语音对讲" : "语音播报";
        gb28181BizExecutor.execute(() -> {
            try {
                gbBroadcastService.onDeviceInvite(request, device, finalChannelId, deviceSdp);
            } catch (Exception e) {
                log.error("[{}] 处理设备反向 INVITE 异常 device={} channel={}",
                        logTag, device.getDeviceId(), finalChannelId, e);
            }
        });
    }

    /**
     * 解析被点播通道 ID：Subject 头 {@code <ChannelID>:<SSRC>,...} 的 result[0] 优先，
     * 回退到 Request-URI 的 user 段。
     */
    private String resolveChannelId(SIPRequest request) {
        String[] subject = SipUtils.getChannelIdFromSubject(request);
        if (subject != null && subject[0] != null && !subject[0].isBlank()) {
            return subject[0];
        }
        // 回退：Request-URI user
        if (request.getRequestURI() instanceof gov.nist.javax.sip.address.SipUri uri) {
            return uri.getUser();
        }
        return null;
    }
}
