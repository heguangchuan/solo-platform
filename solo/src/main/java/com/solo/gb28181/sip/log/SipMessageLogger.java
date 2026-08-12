package com.solo.gb28181.sip.log;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbInviteSessionService;
import gov.nist.javax.sip.message.SIPMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.header.CSeqHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.Set;

/**
 * 场景化 SIP 报文日志。
 *
 * <p>在收发两个收口点（{@code SipSender} 出站、{@code SipProcessorObserver} 入站）被调用，
 * 按 {@code gb28181.sip-message-log.*} 开关决定是否打印报文全文。日志走本项目 logger
 * （{@code com.solo} 下），不需要配合 {@code logging.level.gov.nist}。</p>
 *
 * <h3>判定顺序（性能关键，不要调换）</h3>
 * <ol>
 *   <li>开关关闭 → 立即返回，零开销</li>
 *   <li>method 不在 {@link #PLAY_METHODS} 内 → 立即返回，<b>不查 Redis</b>。
 *       目录同步的上万条 MESSAGE 在这一步全部被挡掉</li>
 *   <li>Call-ID 反查会话，{@code sessionType == PLAY} 才打印</li>
 * </ol>
 *
 * <p>新增场景（回放 / 云台 …）时：在 {@link Gb28181Properties.SipMessageLog} 加开关字段，
 * 这里加一个对应的 method 集合与场景判定分支。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SipMessageLogger {

    /** 点播场景涉及的 method；响应按其 CSeq 的 method 归类 */
    private static final Set<String> PLAY_METHODS = Set.of(Request.INVITE, Request.ACK, Request.BYE);

    private final Gb28181Properties properties;
    private final GbInviteSessionService inviteSessionService;

    /** 出站报文（我方发出） */
    public void logOutbound(Message message) {
        log(message, "→ OUT");
    }

    /** 入站报文（对端发来） */
    public void logInbound(Message message) {
        log(message, "← IN");
    }

    private void log(Message message, String direction) {
        try {
            if (!(message instanceof SIPMessage sipMessage)) {
                return;
            }
            if (!properties.getSipMessageLog().isPlay()) {
                return;
            }
            String method = methodOf(message);
            if (method == null || !PLAY_METHODS.contains(method)) {
                return;
            }
            String callId = sipMessage.getCallIdHeader() == null
                    ? null : sipMessage.getCallIdHeader().getCallId();
            if (callId == null) {
                return;
            }
            GbInviteSession session = inviteSessionService.getByCallId(callId);
            if (session == null || !GbInviteSession.TYPE_PLAY.equals(session.getSessionType())) {
                return;
            }
            log.info("[点播报文] {} {} streamId={}\n{}",
                    direction, describe(message, method), session.getStreamId(),
                    SipMessageRenderer.render(sipMessage));
        } catch (Exception e) {
            // 日志绝不能影响 SIP 收发
            log.warn("[点播报文] 打印失败: {}", e.getMessage());
        }
    }

    /** 请求取自身 method；响应没有 method，取 CSeq 头里的 */
    private String methodOf(Message message) {
        if (message instanceof Request request) {
            return request.getMethod();
        }
        CSeqHeader cseq = (CSeqHeader) message.getHeader(CSeqHeader.NAME);
        return cseq == null ? null : cseq.getMethod();
    }

    /** 报文摘要：请求打 method，响应打 "状态码 method" */
    private String describe(Message message, String method) {
        if (message instanceof Response response) {
            return response.getStatusCode() + " (" + method + ")";
        }
        return method;
    }
}
