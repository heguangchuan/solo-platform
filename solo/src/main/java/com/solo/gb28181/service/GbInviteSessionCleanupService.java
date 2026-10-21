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
}
