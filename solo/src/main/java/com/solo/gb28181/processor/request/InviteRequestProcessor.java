package com.solo.gb28181.processor.request;

import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.service.impl.CascadeSendService;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * <p>当前仅处理 <strong>上级平台点播</strong>本平台共享通道的场景（S6.b-3）：
 * 上级发 INVITE → 本平台向真实下级取流 → sendRtp 推给上级。设备主动 INVITE（语音对讲）暂不支持。</p>
 *
 * <h3>来源识别</h3>
 * <p>复用 {@link MessageRequestProcessor} 的模式：按 From-user 查
 * {@link GbParentPlatform#getServerGbId()}（enabled=1）。命中即上级点播；未命中回 403。</p>
 *
 * <h3>线程模型（ADR-007）</h3>
 * <p>先在 SIP 线程内同步回 100 Trying，随后把「取流（阻塞等流就绪）+ startSendRtp + 回 200」
 * 整体丢到 {@code gb28181BizExecutor} 异步执行，绝不阻塞 SIP 监听线程。</p>
 *
 * <h3>循环依赖</h3>
 * <p>{@link CascadeSendService} 用 {@code @Lazy} 注入，打破
 * {@code SipLayer → SipProcessorObserver → InviteRequestProcessor → CascadeSendService → GbPlayService → SipLayer} 环（ADR-015）。</p>
 */
@Slf4j
@Component
public class InviteRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    @Autowired
    private GbParentPlatformService gbParentPlatformService;

    @Autowired
    @Lazy
    private CascadeSendService cascadeSendService;

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

        // 识别上级平台（作为下级被上级点播）
        GbParentPlatform platform = gbParentPlatformService.lambdaQuery()
                .eq(GbParentPlatform::getServerGbId, fromUser)
                .eq(GbParentPlatform::getEnabled, 1)
                .one();
        if (platform == null) {
            log.warn("[级联点播] 收到非已知上级平台的 INVITE from={}，回 403", fromUser);
            responseAck(request, Response.FORBIDDEN, "unknown source");
            return;
        }

        // 解析被点播通道国标编号（Subject 优先，回退 Request-URI user）
        String channelId = resolveChannelId(request);
        if (channelId == null || channelId.isBlank()) {
            log.warn("[级联点播] 上级 {} INVITE 无法解析 channelId，回 400", platform.getServerGbId());
            responseAck(request, Response.BAD_REQUEST, "no channel id");
            return;
        }

        byte[] raw = request.getRawContent();
        String upstreamSdp = raw == null ? null : new String(raw, java.nio.charset.StandardCharsets.UTF_8);
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
