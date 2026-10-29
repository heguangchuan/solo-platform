package com.solo.gb28181.task;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbInviteSessionCleanupService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 启动接管用例：确认还活着的会话要保留，确认已经死了的才清掉。
 */
@ExtendWith(MockitoExtension.class)
class InviteSessionTakeoverRunnerTest {

    private static final String STREAM = "34020000001320000001_34020000001310000001";
    private static final String SERVER_ID = "zlm-1";

    @Mock
    private GbInviteSessionService inviteSessionService;
    @Mock
    private GbInviteSessionCleanupService inviteCleanup;
    @Mock
    private ZlmRestClient zlmRestClient;
    @Mock
    private GbMediaServerMapper mediaServerMapper;

    @InjectMocks
    private InviteSessionTakeoverRunner runner;

    private GbInviteSession session(String type, String status, String toTag) {
        GbInviteSession s = new GbInviteSession();
        s.setStreamId(STREAM);
        s.setServerId(SERVER_ID);
        s.setDeviceId("34020000001320000001");
        s.setChannelId("34020000001310000001");
        s.setSessionType(type);
        s.setStatus(status);
        s.setToTag(toTag);
        s.setStartTime(LocalDateTime.now().minusHours(1));
        return s;
    }

    private MediaServer server() {
        MediaServer s = new MediaServer();
        s.setServerId(SERVER_ID);
        s.setStatus(1);
        return s;
    }

    private void givenMediaServer() {
        when(mediaServerMapper.selectList(any())).thenReturn(List.of(server()));
    }

    @Test
    void keepsReadySessionWhenZlmStillHasStream() {
        GbInviteSession s = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_READY, "to-tag");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(s));
        givenMediaServer();
        when(zlmRestClient.isMediaOnlineOrNull(any(), eq("rtp"), eq(STREAM))).thenReturn(true);

        runner.takeover();

        verify(inviteCleanup, never()).failSession(any(), anyString(), anyString());
        verify(inviteCleanup, never()).failAndBye(any(), anyString(), anyString());
    }

    @Test
    void cleansReadySessionWhenStreamIsGone() {
        GbInviteSession s = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_READY, "to-tag");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(s));
        givenMediaServer();
        when(zlmRestClient.isMediaOnlineOrNull(any(), eq("rtp"), eq(STREAM))).thenReturn(false);

        runner.takeover();

        // 有 to-tag = dialog 建立过，收尾要发 BYE
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(inviteCleanup).failAndBye(eq(s), anyString(), reason.capture());
        assertThat(reason.getValue()).contains("流已不在");
    }

    @Test
    void keepsReadySessionWhenNodeUnreachable() {
        GbInviteSession s = session(GbInviteSession.TYPE_RECORD, GbInviteSession.STATUS_READY, "to-tag");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(s));
        givenMediaServer();
        // 节点连不上，判断不了流在不在，先保留
        when(zlmRestClient.isMediaOnlineOrNull(any(), eq("rtp"), eq(STREAM))).thenReturn(null);

        runner.takeover();

        verify(inviteCleanup, never()).failSession(any(), anyString(), anyString());
        verify(inviteCleanup, never()).failAndBye(any(), anyString(), anyString());
    }

    @Test
    void cleansInvitingSessionWithoutDialog() {
        GbInviteSession s = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_INVITING, null);
        when(inviteSessionService.listAllActive()).thenReturn(List.of(s));

        runner.takeover();

        // 没有 to-tag 就发不出 BYE，也不能去探 ZLM（本来就没流）
        verify(inviteCleanup).failSession(eq(s), anyString(), anyString());
        verify(inviteCleanup, never()).failAndBye(any(), anyString(), anyString());
        verify(zlmRestClient, never()).isMediaOnlineOrNull(any(), anyString(), anyString());
    }

    @Test
    void cleansInvitingSessionWithDialogViaBye() {
        GbInviteSession s = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_INVITING, "to-tag");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(s));

        runner.takeover();

        verify(inviteCleanup).failAndBye(eq(s), anyString(), anyString());
    }

    @Test
    void cleansDownloadSessionEvenIfStreamLooksOnline() {
        GbInviteSession s = session(GbInviteSession.TYPE_DOWNLOAD, GbInviteSession.STATUS_READY, "to-tag");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(s));

        runner.takeover();

        // 任务表在进程内，流再活也续不上，直接收尾
        verify(inviteCleanup).failAndBye(eq(s), anyString(), anyString());
        verify(zlmRestClient, never()).isMediaOnlineOrNull(any(), anyString(), anyString());
    }

    @Test
    void cleansTerminalSessions() {
        GbInviteSession stopped = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_STOPPED, null);
        GbInviteSession failed = session(GbInviteSession.TYPE_RECORD, GbInviteSession.STATUS_FAILED, "to-tag");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(stopped, failed));

        runner.takeover();

        verify(inviteCleanup).failSession(eq(stopped), anyString(), anyString());
        verify(inviteCleanup).failAndBye(eq(failed), anyString(), anyString());
    }

    @Test
    void keepsSessionsOnOfflineNodeWithoutProbing() {
        GbInviteSession s = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_READY, "to-tag");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(s));
        MediaServer offline = server();
        offline.setStatus(0);
        when(mediaServerMapper.selectList(any())).thenReturn(List.of(offline));

        runner.takeover();

        // 节点离线判定不了流是否还在，保留会话；也不去探测，避免启动时一个个等超时
        verify(inviteCleanup, never()).failSession(any(), anyString(), anyString());
        verify(inviteCleanup, never()).failAndBye(any(), anyString(), anyString());
        verify(zlmRestClient, never()).isMediaOnlineOrNull(any(), anyString(), anyString());
    }

    @Test
    void probesDeadNodeOnlyOncePerRun() {
        GbInviteSession first = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_READY, "to-tag");
        GbInviteSession second = session(GbInviteSession.TYPE_RECORD, GbInviteSession.STATUS_READY, "to-tag");
        second.setStreamId(STREAM + "-2");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(first, second));
        givenMediaServer();
        when(zlmRestClient.isMediaOnlineOrNull(any(), eq("rtp"), anyString())).thenReturn(null);

        runner.takeover();

        verify(zlmRestClient, times(1)).isMediaOnlineOrNull(any(), eq("rtp"), anyString());
    }

    @Test
    void cleansReadySessionWhenServingNodeIsGone() {
        GbInviteSession s = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_READY, "to-tag");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(s));
        when(mediaServerMapper.selectList(any())).thenReturn(List.of());

        runner.takeover();

        verify(inviteCleanup).failAndBye(eq(s), anyString(), anyString());
        verify(zlmRestClient, never()).isMediaOnlineOrNull(any(), anyString(), anyString());
    }

    @Test
    void leavesSessionsCreatedByThisProcessAlone() {
        GbInviteSession fresh = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_INVITING, null);
        fresh.setStartTime(LocalDateTime.now().plusSeconds(60));
        when(inviteSessionService.listAllActive()).thenReturn(List.of(fresh));

        runner.takeover();

        verify(inviteCleanup, never()).failSession(any(), anyString(), anyString());
        verify(inviteCleanup, never()).failAndBye(any(), anyString(), anyString());
    }

    @Test
    void oneBadSessionDoesNotAbortTheRest() {
        GbInviteSession broken = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_READY, "to-tag");
        GbInviteSession parallel = session(GbInviteSession.TYPE_PLAY, GbInviteSession.STATUS_READY, "to-tag");
        parallel.setStreamId(STREAM + "-2");
        when(inviteSessionService.listAllActive()).thenReturn(List.of(broken, parallel));
        givenMediaServer();
        when(zlmRestClient.isMediaOnlineOrNull(any(), eq("rtp"), eq(STREAM)))
                .thenThrow(new RuntimeException("boom"));
        when(zlmRestClient.isMediaOnlineOrNull(any(), eq("rtp"), eq(STREAM + "-2"))).thenReturn(true);

        runner.takeover();

        verify(inviteCleanup, never()).failAndBye(eq(parallel), anyString(), anyString());
    }
}
