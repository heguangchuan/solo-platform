package com.solo.media.hook;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbCloudRecordService;
import com.solo.gb28181.service.GbDownloadService;
import com.solo.gb28181.service.GbInviteSessionService;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.gb28181.service.GbRecordFileService;
import com.solo.gb28181.service.impl.CascadeSendService;
import com.solo.gb28181.service.impl.GbBroadcastService;
import com.solo.media.entity.GbViewRecord;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.mapper.StreamPushClientMapper;
import com.solo.media.properties.MediaProperties;
import com.solo.media.service.GbViewRecordService;
import com.solo.media.service.MediaServerLoader;
import com.solo.media.service.StreamProxyService;
import com.solo.media.service.StreamPushService;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewTokenSigner;
import com.solo.onvif.service.OnvifPlayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZlmHookServiceTest {

    private static final String VALID = "valid-token";
    private static final PlayTarget TARGET = PlayTarget.httpFlv("server-1", "rtp", "device_channel");
    private static final ViewTokenSigner.ViewToken TOKEN = new ViewTokenSigner.ViewToken(
            1, 7L, "tester", TARGET.mediaServerId(), TARGET.app(), TARGET.streamId(), TARGET.schema());

    @Spy
    private MediaProperties mediaProperties = new MediaProperties();
    @Mock private GbMediaServerMapper mediaServerMapper;
    @Mock private StreamPushClientMapper streamPushClientMapper;
    @Mock private MediaServerLoader loadIndex;
    @Mock private GbInviteSessionService inviteSessionService;
    @Mock private GbPlayService playService;
    @Mock private GbPlaybackService playbackService;
    @Mock private GbDownloadService downloadService;
    @Mock private GbCloudRecordService cloudRecordService;
    @Mock private GbRecordFileService recordFileService;
    @Mock private StreamPushService streamPushService;
    @Mock private StreamProxyService streamProxyService;
    @Mock private CascadeSendService cascadeSendService;
    @Mock private GbBroadcastService gbBroadcastService;
    @Mock private OnvifPlayService onvifPlayService;
    @Mock private GbViewRecordService viewRecordService;
    @Mock private ViewTokenSigner viewTokenSigner;
    @Mock private InternalPlayAuthorizer internalPlayAuthorizer;

    @InjectMocks
    private ZlmHookService hookService;

    @BeforeEach
    void setUp() {
        lenient().when(viewTokenSigner.verify(VALID, TARGET)).thenReturn(TOKEN);
    }

    @Test
    void onPlayRejectsMissingInvalidExpiredAndCrossTargetTokens() {
        assertThat(code(hookService.onPlay(playBody(null)))).isEqualTo(-1);
        assertThat(code(hookService.onPlay(playBody("bad-token")))).isEqualTo(-1);
        assertThat(code(hookService.onPlay(playBody("expired-token")))).isEqualTo(-1);

        Map<String, Object> crossStream = playBody(VALID);
        crossStream.put("stream", "other_stream");
        assertThat(code(hookService.onPlay(crossStream))).isEqualTo(-1);
        Map<String, Object> crossSchema = playBody(VALID);
        crossSchema.put("schema", "hls");
        assertThat(code(hookService.onPlay(crossSchema))).isEqualTo(-1);
        Map<String, Object> crossProtocol = playBody(VALID);
        crossProtocol.put("protocol", "ws");
        assertThat(code(hookService.onPlay(crossProtocol))).isEqualTo(-1);
        Map<String, Object> crossServer = playBody(VALID);
        crossServer.put("mediaServerId", "server-2");
        assertThat(code(hookService.onPlay(crossServer))).isEqualTo(-1);

        verifyNoInteractions(viewRecordService);
    }

    @Test
    void onPlayAllowsValidHtmlEncodedTokenAndAuditsVerifiedIdentity() {
        Map<String, Object> body = playBody(null);
        body.put("params", "wt&#61;" + VALID + "&#38;other&#61;1");
        GbInviteSession session = new GbInviteSession();
        session.setDeviceId("device");
        session.setChannelId("channel");
        when(inviteSessionService.getByStreamId(TARGET.streamId())).thenReturn(session);

        assertThat(code(hookService.onPlay(body))).isZero();

        ArgumentCaptor<GbViewRecord> captor = ArgumentCaptor.forClass(GbViewRecord.class);
        verify(viewRecordService).openRecord(captor.capture());
        GbViewRecord record = captor.getValue();
        assertThat(record.getActorId()).isEqualTo(7L);
        assertThat(record.getActorName()).isEqualTo("tester");
        assertThat(record.getMediaServerId()).isEqualTo("server-1");
        assertThat(record.getApp()).isEqualTo("rtp");
        assertThat(record.getStreamId()).isEqualTo("device_channel");
        assertThat(record.getStreamSchema()).isEqualTo("rtmp");
        assertThat(record.getDeviceId()).isEqualTo("device");
        assertThat(record.getChannelId()).isEqualTo("channel");
    }

    @Test
    void flvHookWithoutProtocolUsesBothFlvTargets() {
        PlayTarget wsTarget = PlayTarget.wsFlv("server-1", "rtp", "device_channel");
        List<PlayTarget> candidates = List.of(TARGET, wsTarget);
        when(viewTokenSigner.verifyAny(VALID, candidates)).thenReturn(TOKEN);
        Map<String, Object> body = playBody(VALID);
        body.remove("protocol");

        assertThat(code(hookService.onPlay(body))).isZero();
        assertThat(code(hookService.onStreamNotFound(body))).isZero();
    }

    @Test
    void disablingViewRecordOnlySkipsAuditAndDoesNotBypassAuthorization() {
        mediaProperties.getViewRecord().setEnabled(false);

        assertThat(code(hookService.onPlay(playBody(VALID)))).isZero();
        assertThat(code(hookService.onPlay(playBody("bad-token")))).isEqualTo(-1);
        verifyNoInteractions(viewRecordService);
    }

    @Test
    void auditFailureDoesNotChangeAuthorizedPlaybackResult() {
        doThrow(new IllegalStateException("database unavailable")).when(viewRecordService).openRecord(any());

        assertThat(code(hookService.onPlay(playBody(VALID)))).isZero();
    }

    @Test
    void streamNotFoundRejectsInvalidTokenWithoutInvite() {
        assertStreamRejected(playBody(null));
        assertStreamRejected(playBody("bad-token"));

        Map<String, Object> crossStream = playBody(VALID);
        crossStream.put("stream", "other_stream");
        assertStreamRejected(crossStream);
        verify(playService, never()).autoStartPlay(any(), any(), any());
    }

    @Test
    void streamNotFoundAllowsOnlyAuthorizedGbMainStreamToAutoStart() {
        assertThat(code(hookService.onStreamNotFound(playBody(VALID)))).isZero();
        verify(playService).autoStartPlay("device", "channel", "server-1");

        PlayTarget proxyTarget = PlayTarget.httpFlv("server-1", "proxy", "device_channel");
        ViewTokenSigner.ViewToken proxyToken = new ViewTokenSigner.ViewToken(
                1, 7L, "tester", proxyTarget.mediaServerId(), proxyTarget.app(), proxyTarget.streamId(), proxyTarget.schema());
        when(viewTokenSigner.verify(VALID, proxyTarget)).thenReturn(proxyToken);
        Map<String, Object> proxyBody = playBody(VALID);
        proxyBody.put("app", "proxy");
        assertThat(code(hookService.onStreamNotFound(proxyBody))).isZero();
        verify(playService).autoStartPlay("device", "channel", "server-1");
    }

    @Test
    void internalReaderIsPreciselyAuthorizedButCannotAutoStart() {
        when(internalPlayAuthorizer.isAuthorized("reader-1", "server-1", "rtp", "device_channel", "rtp"))
                .thenReturn(true);
        Map<String, Object> body = playBody(null);
        body.put("schema", "rtp");

        assertThat(code(hookService.onPlay(body))).isZero();
        assertThat(code(hookService.onStreamNotFound(body))).isZero();
        verifyNoInteractions(viewRecordService);
        verify(playService, never()).autoStartPlay(any(), any(), any());
    }

    @Test
    void invalidTokenCannotFallBackToInternalReaderWhitelist() {
        Map<String, Object> body = playBody("bad-token");
        body.put("schema", "rtp");

        assertThat(code(hookService.onPlay(body))).isEqualTo(-1);
        verify(internalPlayAuthorizer, never()).isAuthorized(any(), any(), any(), any(), any());
        verifyNoInteractions(viewRecordService);
    }

    @Test
    void missingTargetFieldsAndAuthorizationExceptionsFailClosed() {
        Map<String, Object> missingSchema = playBody(VALID);
        missingSchema.remove("schema");
        assertThat(code(hookService.onPlay(missingSchema))).isEqualTo(-1);

        when(viewTokenSigner.verify("explodes", TARGET)).thenThrow(new IllegalStateException("key failure"));
        assertThat(code(hookService.onPlay(playBody("explodes")))).isEqualTo(-1);
        assertStreamRejected(playBody("explodes"));
        verify(playService, never()).autoStartPlay(any(), any(), any());
    }

    @Test
    void hlsPlaylistAndSegmentsRequireTheSameHlsTokenButRecordFilesAreUntouched() {
        PlayTarget hlsTarget = PlayTarget.hls("server-1", "rtp", "device_channel");
        ViewTokenSigner.ViewToken hlsToken = new ViewTokenSigner.ViewToken(
                1, 7L, "tester", "server-1", "rtp", "device_channel", "hls");
        when(viewTokenSigner.verify(VALID, hlsTarget)).thenReturn(hlsToken);

        assertThat(code(hookService.onHttpAccess(httpBody("/rtp/device_channel/hls.m3u8", null)))).isEqualTo(-1);
        assertThat(code(hookService.onHttpAccess(httpBody("/rtp/device_channel/2026/11/25/1.ts", null)))).isEqualTo(-1);
        assertThat(code(hookService.onHttpAccess(httpBody("/rtp/device_channel/init.mp4", null)))).isEqualTo(-1);
        assertThat(code(hookService.onHttpAccess(httpBody("/rtp/device_channel/hls.m3u8", VALID)))).isZero();
        assertThat(code(hookService.onHttpAccess(httpBody("/rtp/device_channel/2026/11/25/1.ts", VALID)))).isZero();
        assertThat(code(hookService.onHttpAccess(httpBody("//rtp//device_channel/2026/11/25/1.ts", VALID)))).isZero();
        assertThat(code(hookService.onHttpAccess(httpBody("/record/../rtp/device_channel/1.ts", VALID)))).isEqualTo(-1);
        assertThat(code(hookService.onHttpAccess(httpBody("\\rtp\\device_channel\\1.ts", VALID)))).isEqualTo(-1);
        assertThat(code(hookService.onHttpAccess(httpBody("/record/rtp/device_channel/1.mp4", null)))).isZero();
        when(streamProxyService.count(any())).thenReturn(1L);
        assertThat(code(hookService.onHttpAccess(httpBody("/record/camera-1/hls.m3u8", null)))).isEqualTo(-1);
        assertThat(code(hookService.onHttpAccess(httpBody("/rtp/device_channel.live.flv", null)))).isZero();
    }

    private static Map<String, Object> playBody(String token) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "reader-1");
        body.put("mediaServerId", "server-1");
        body.put("app", "rtp");
        body.put("stream", "device_channel");
        body.put("schema", "rtmp");
        body.put("protocol", "http");
        body.put("ip", "127.0.0.1");
        if (token != null) {
            body.put("params", "wt=" + token);
        }
        return body;
    }

    private static Map<String, Object> httpBody(String path, String token) {
        Map<String, Object> body = playBody(token);
        body.put("path", path);
        return body;
    }

    private void assertStreamRejected(Map<String, Object> body) {
        HookResult result = hookService.onStreamNotFound(body);
        assertThat(code(result)).isZero();
        assertThat(result.toMap()).containsEntry("close", true);
    }

    private static int code(HookResult result) {
        return (Integer) result.toMap().get("code");
    }
}
