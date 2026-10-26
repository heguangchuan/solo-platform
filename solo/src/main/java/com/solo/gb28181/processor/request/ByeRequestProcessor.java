package com.solo.gb28181.processor.request;

import com.solo.gb28181.entity.CascadeSendSession;
import com.solo.gb28181.entity.GbBroadcastSession;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.service.CascadeSendSessionService;
import com.solo.gb28181.service.GbBroadcastSessionService;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.gb28181.service.impl.CascadeSendService;
import com.solo.gb28181.service.impl.GbBroadcastService;
import com.solo.gb28181.service.impl.GbPlatformBroadcastService;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * 对端主动 BYE：立即回 200，再按 callId 依次查点播/回放会话、级联推流会话、广播会话并清扫。
 * 本平台主动发出的 BYE，对端回 200 走订阅回调，不经过这里。
 */
@Slf4j
@Component
public class ByeRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    @Autowired
    private GbInviteSessionService inviteSessionService;

    @Autowired
    private CascadeSendSessionService cascadeSendSessionService;

    @Autowired
    private GbBroadcastSessionService gbBroadcastSessionService;

    /** 用 @Lazy 打破循环依赖（PlayService → SipCommander → SipProcessorObserver → BYE processor → PlayService） */
    @Autowired
    @Lazy
    private GbPlayService playService;

    @Autowired
    @Lazy
    private GbPlaybackService playbackService;

    @Autowired
    @Lazy
    private CascadeSendService cascadeSendService;

    /** 语音广播：设备停播 BYE，按广播会话清扫 */
    @Autowired
    @Lazy
    private GbBroadcastService gbBroadcastService;

    /** 级联喊话：上级停喊 BYE，停设备腿并清场 */
    @Autowired
    @Lazy
    private GbPlatformBroadcastService platformBroadcastService;

    /** 同样用 @Lazy 避开 SIP 组件初始化期的循环依赖 */
    @Autowired
    @Lazy
    private GbCloudRecordService cloudRecordService;

    @Override
    public String getMethod() {
        return Request.BYE;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SIPRequest request = (SIPRequest) event.getRequest();
        // 1) 立即回 200 OK，避免设备 BYE 重传
        responseAck(request, Response.OK);

        // 2) 按 callId 查会话
        CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        String callId = callIdHeader == null ? null : callIdHeader.getCallId();
        if (callId == null) {
            log.warn("[设备 BYE] 缺 Call-Id");
            return;
        }
        GbInviteSession session = inviteSessionService.getByCallId(callId);
        if (session == null) {
            // 设备/回放会话未命中：可能是上级停播（级联推流会话），转查
            CascadeSendSession sendSession = cascadeSendSessionService.getByCallId(callId);
            if (sendSession != null) {
                log.info("[级联点播] 上级停播 BYE 上级平台={} 本平台通道编码={} callId={}",
                        sendSession.getPlatformServerGbId(), sendSession.getLocalChannelId(), callId);
                cascadeSendService.onUpstreamBye(sendSession);
                return;
            }
            // 广播会话的 BYE：设备腿或级联喊话的上级腿，靠 parentCallId 区分
            GbBroadcastSession broadcastSession = gbBroadcastSessionService.getByCallId(callId);
            if (broadcastSession != null) {
                if (callId.equals(broadcastSession.getParentCallId())
                        && !callId.equals(broadcastSession.getCallId())) {
                    log.info("[级联喊话] 上级停喊 BYE 流={} callId={}",
                            broadcastSession.getBroadcastStreamId(), callId);
                    platformBroadcastService.onUpstreamBye(broadcastSession);
                } else {
                    log.info("[语音广播] 设备停播 BYE 设备={} 通道={} callId={}",
                            broadcastSession.getDeviceId(), broadcastSession.getChannelId(), callId);
                    gbBroadcastService.onDeviceBye(broadcastSession);
                }
                return;
            }
            log.info("[设备 BYE] callId={} 无对应会话（可能已被清扫）", callId);
            return;
        }
        log.info("[设备 BYE] stream={} callId={} sessionType={}",
                session.getStreamId(), callId, session.getSessionType());
        if (GbInviteSession.TYPE_RECORD.equals(session.getSessionType())) {
            cloudRecordService.onDeviceBye(session);
        } else if (GbInviteSession.TYPE_PLAYBACK.equals(session.getSessionType())
                || GbInviteSession.TYPE_DOWNLOAD.equals(session.getSessionType())) {
            playbackService.onDeviceBye(session);
        } else {
            playService.onDeviceBye(session);
        }
    }
}
