package com.solo.media.hook;

import com.solo.gb28181.entity.CascadeSendSession;
import com.solo.gb28181.entity.GbBroadcastSession;
import com.solo.gb28181.service.CascadeSendSessionService;
import com.solo.gb28181.service.GbBroadcastSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// startSendRtp 的内部 reader 没有 wt，只能拿同节点、同 app、同源流的在途会话来认。
// 别图省事只按 app 或流名前缀放行。
@Component
@RequiredArgsConstructor
public class InternalPlayAuthorizer {

    // 定制版 ZLM 也可能发 on_play，不能靠“标准版不会调”来放行
    static final String INTERNAL_RTP_SCHEMA = "rtp";

    private final CascadeSendSessionService cascadeSendSessionService;
    private final GbBroadcastSessionService broadcastSessionService;

    public boolean isAuthorized(String readerId, String mediaServerId, String app, String stream, String schema) {
        if (isBlank(readerId) || isBlank(mediaServerId) || isBlank(app) || isBlank(stream)
                || !INTERNAL_RTP_SCHEMA.equalsIgnoreCase(schema)) {
            return false;
        }
        return matchesCascade(mediaServerId, app, stream) || matchesBroadcast(mediaServerId, app, stream);
    }

    private boolean matchesCascade(String mediaServerId, String app, String stream) {
        for (CascadeSendSession session : cascadeSendSessionService.listBySrcStream(stream)) {
            if (!CascadeSendSession.STATUS_STOPPED.equals(session.getStatus())
                    && mediaServerId.equals(session.getServerId())
                    && app.equals(session.getSrcApp())
                    && stream.equals(session.getSrcStream())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBroadcast(String mediaServerId, String app, String stream) {
        for (GbBroadcastSession session : broadcastSessionService.listBySrcStream(stream)) {
            if (!GbBroadcastSession.STATUS_STOPPED.equals(session.getStatus())
                    && mediaServerId.equals(session.getServerId())
                    && app.equals(session.getSrcApp())
                    && stream.equals(session.getSrcStream())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
