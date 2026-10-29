package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.media.entity.MediaServer;

public interface GbInviteSessionCleanupService {

    int openRtpServerWithRetry(MediaServer server, String streamId, int tcpMode);

    void cleanupZlm(GbInviteSession session);

    void forceCleanup(GbInviteSession session);

    void failSession(GbInviteSession session, String logTag, String reason);

    void failAndBye(GbInviteSession session, String logTag, String reason);

    void timeoutSession(GbInviteSession session, String logTag, String reason);

    /**
     * 会话已经没了，但 ZLM 上可能还挂着流：挨个节点探一遍，碰到就关掉。
     * 没会话发不出 BYE，设备那边只能等自己超时，至少别让流白占着端口和带宽。
     *
     * @return 有没有真的关掉一路流
     */
    boolean closeOrphanStream(String streamId);
}
