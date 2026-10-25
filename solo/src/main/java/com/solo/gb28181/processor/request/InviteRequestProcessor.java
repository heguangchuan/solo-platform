package com.solo.gb28181.processor.request;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * <p>处理两类入站 INVITE：</p>
 * <ol>
 *   <li><strong>上级平台点播</strong>本平台共享通道（S6.b-3）：上级发 INVITE → 本平台向真实下级取流
 *       → sendRtp 推给上级；</li>
 *   <li><strong>设备语音广播反向 INVITE</strong>：设备收到 Broadcast 通知后，主动发 m=audio 的 INVITE
 *       来建音频通道（设备 a=recvonly），平台回 200 后由 ZLM 推音频。</li>
 * </ol>
 *
 * <h3>来源识别</h3>
 * <p>复用 {@link MessageRequestProcessor} 的模式：先按 From-user 查
 * {@link GbParentPlatform#getServerGbId()}（enabled=1），命中即上级点播；未命中再查在线设备
 * {@link GbDevice#getDeviceId()}（status=1）且 SDP 为 m=audio，命中即语音广播；都不命中回 403。</p>
 *
 * <h3>线程模型（ADR-007）</h3>
 * <p>先在 SIP 线程内同步回 100 Trying，随后把业务处理（取流/等浏览器音频流 + startSendRtp + 回 200）
 * 整体丢到 {@code gb28181BizExecutor} 异步执行，绝不阻塞 SIP 监听线程。</p>
 *
 * <h3>循环依赖</h3>
 * <p>{@link CascadeSendService} / {@link GbBroadcastService} 用 {@code @Lazy} 注入，打破
 * {@code SipLayer → SipProcessorObserver → InviteRequestProcessor → ... → SipLayer} 环（ADR-015）。</p>
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

        // 2) 取流 + 推流 + 回 200 丢到业务线程池
        gb28181BizExecutor.execute(() -> {
            try {
                cascadeSendService.onUpstreamInvite(request, platform, channelId, upstreamSdp);
            } catch (Exception e) {
                log.error("[级联点播] 处理上级点播异常 platform={} channel={}",
                        platform.getServerGbId(), channelId, e);
            }
        });
    }

    /** 设备语音广播：通道码取 Request-URI user（设备 INVITE 通常不带 Subject），100 后丢业务线程池 */
    private void handleBroadcastInvite(SIPRequest request, GbDevice device, String deviceSdp)
            throws SipException, ParseException {
        String channelId = resolveChannelId(request);
        if (channelId == null || channelId.isBlank()) {
            log.warn("[语音广播] 设备 {} INVITE 无法解析通道码，回 400", device.getDeviceId());
            responseAck(request, Response.BAD_REQUEST, "no channel id");
            return;
        }

        responseAck(request, Response.TRYING);

        gb28181BizExecutor.execute(() -> {
            try {
                gbBroadcastService.onDeviceInvite(request, device, channelId, deviceSdp);
            } catch (Exception e) {
                log.error("[语音广播] 处理设备反向 INVITE 异常 device={} channel={}",
                        device.getDeviceId(), channelId, e);
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
