package com.solo.gb28181.service.impl;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbBroadcastSession;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbParentPlatformMapper;
import com.solo.gb28181.request.BroadcastStartRequest;
import com.solo.gb28181.request.BroadcastWebRtcRequest;
import com.solo.gb28181.response.BroadcastResultResponse;
import com.solo.gb28181.service.GbBroadcastSessionService;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import com.solo.media.client.ZlmRestClient;
import com.solo.media.entity.MediaServer;
import com.solo.media.mapper.GbMediaServerMapper;
import com.solo.media.service.MediaServerSelector;
import com.solo.media.ssrc.SsrcFactory;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GbBroadcastServicePlayAuthTest {

    @Mock private Gb28181Properties properties;
    @Mock private GbBroadcastSessionService sessionService;
    @Mock private GbDeviceMapper deviceMapper;
    @Mock private GbDeviceChannelMapper channelMapper;
    @Mock private MediaServerSelector mediaServerSelector;
    @Mock private GbMediaServerMapper mediaServerMapper;
    @Mock private SsrcFactory ssrcFactory;
    @Mock private ZlmRestClient zlmRestClient;
    @Mock private SipCommander sipCommander;
    @Mock private SipRequestHeaderProvider headerProvider;
    @Mock private SipLayer sipLayer;
    @Mock private SipSender sipSender;
    @Mock private GbParentPlatformMapper platformMapper;
    @Mock private PlatformCommander platformCommander;
    @Mock private ViewTokenSigner viewTokenSigner;
    @InjectMocks private GbBroadcastService broadcastService;

    private MediaServer server;
    private GbBroadcastSession session;

    @BeforeEach
    void setUp() {
        server = new MediaServer();
        server.setServerId("server-1");
        server.setHostIp("127.0.0.1");
        server.setStreamIp("media.example");
        server.setRtcPort(8000);

        session = new GbBroadcastSession();
        session.setBroadcastStreamId("broadcast_device_channel");
        session.setServerId("server-1");
        session.setDeviceId("device");
        session.setChannelId("channel");
        session.setSrcApp("broadcast");
        session.setSrcStream("broadcast_device_channel");
        session.setMode(GbBroadcastSession.MODE_TALK);
        session.setStatus(GbBroadcastSession.STATUS_SENDING);
        session.setRecvServerId("server-1");
        session.setRecvStreamId("recv_device_channel");
    }

    @Test
    void browserResultSignsOnlyReceiveUrlAndNeverPushUrl() {
        when(mediaServerSelector.pick()).thenReturn(server);
        when(deviceMapper.selectOne(any())).thenReturn(onlineDevice());
        when(channelMapper.selectOne(any())).thenReturn(onlineChannel());
        when(sessionService.get(session.getBroadcastStreamId())).thenReturn(session);
        ViewActor actor = ViewActor.user(7L, "tester");
        when(viewTokenSigner.requireCurrentUserActor()).thenReturn(actor);
        PlayTarget target = PlayTarget.rtc("server-1", "rtp", "recv_device_channel");
        when(viewTokenSigner.appendToken(anyString(), eq(actor), eq(target)))
                .thenAnswer(invocation -> invocation.getArgument(0) + "&wt=play-token");
        BroadcastStartRequest request = new BroadcastStartRequest();
        request.setDeviceId("device");
        request.setChannelId("channel");
        request.setMode("talk");

        BroadcastResultResponse result = broadcastService.start(request);

        assertThat(result.getRtcPushUrl()).contains("type=push").doesNotContain("wt=");
        assertThat(result.getRtcPlayUrl()).contains("type=play").contains("wt=play-token");
        verify(viewTokenSigner).appendToken(anyString(), eq(actor), eq(target));
    }

    @Test
    void proxiedReceiveSdpCarriesIssuedRtcToken() {
        when(sessionService.get(session.getBroadcastStreamId())).thenReturn(session);
        when(mediaServerMapper.selectOne(any())).thenReturn(server);
        when(zlmRestClient.isMediaOnline(server, "rtp", "recv_device_channel")).thenReturn(true);
        ViewActor actor = ViewActor.user(7L, "tester");
        when(viewTokenSigner.requireCurrentUserActor()).thenReturn(actor);
        PlayTarget target = PlayTarget.rtc("server-1", "rtp", "recv_device_channel");
        when(viewTokenSigner.issueToken(actor, target)).thenReturn("play-token");
        when(zlmRestClient.exchangeWebRtcSdp(server, "rtp", "recv_device_channel", "play", "offer", "play-token"))
                .thenReturn("answer");
        BroadcastWebRtcRequest request = new BroadcastWebRtcRequest();
        request.setStreamId(session.getBroadcastStreamId());
        request.setSdp("offer");

        assertThat(broadcastService.exchangeRecvWebRtcSdp(request)).isEqualTo("answer");

        verify(zlmRestClient).exchangeWebRtcSdp(server, "rtp", "recv_device_channel", "play", "offer", "play-token");
    }

    @Test
    void pushSdpDoesNotAcquireOrAttachPlaybackToken() {
        when(sessionService.get(session.getBroadcastStreamId())).thenReturn(session);
        when(mediaServerMapper.selectOne(any())).thenReturn(server);
        when(zlmRestClient.exchangeWebRtcSdp(server, "broadcast", session.getBroadcastStreamId(), "push", "offer"))
                .thenReturn("answer");
        BroadcastWebRtcRequest request = new BroadcastWebRtcRequest();
        request.setStreamId(session.getBroadcastStreamId());
        request.setSdp("offer");

        assertThat(broadcastService.exchangeWebRtcSdp(request)).isEqualTo("answer");

        verify(zlmRestClient).exchangeWebRtcSdp(server, "broadcast", session.getBroadcastStreamId(), "push", "offer");
        verifyNoInteractions(viewTokenSigner);
    }

    private static GbDevice onlineDevice() {
        GbDevice device = new GbDevice();
        device.setDeviceId("device");
        device.setStatus(1);
        return device;
    }

    private static GbDeviceChannel onlineChannel() {
        GbDeviceChannel channel = new GbDeviceChannel();
        channel.setDeviceId("device");
        channel.setChannelId("channel");
        channel.setStatus("ON");
        return channel;
    }
}
