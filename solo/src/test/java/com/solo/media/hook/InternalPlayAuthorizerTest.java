package com.solo.media.hook;

import com.solo.gb28181.entity.CascadeSendSession;
import com.solo.gb28181.entity.GbBroadcastSession;
import com.solo.gb28181.service.CascadeSendSessionService;
import com.solo.gb28181.service.GbBroadcastSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InternalPlayAuthorizerTest {

    @Mock
    private CascadeSendSessionService cascadeSendSessionService;
    @Mock
    private GbBroadcastSessionService broadcastSessionService;
    @InjectMocks
    private InternalPlayAuthorizer authorizer;

    private CascadeSendSession cascade;
    private GbBroadcastSession broadcast;

    @BeforeEach
    void setUp() {
        cascade = new CascadeSendSession();
        cascade.setServerId("server-1");
        cascade.setSrcApp("rtp");
        cascade.setSrcStream("device_channel");
        cascade.setStatus(CascadeSendSession.STATUS_SENDING);

        broadcast = new GbBroadcastSession();
        broadcast.setServerId("server-1");
        broadcast.setSrcApp("broadcast");
        broadcast.setSrcStream("broadcast_1");
        broadcast.setStatus(GbBroadcastSession.STATUS_SENDING);

        lenient().when(cascadeSendSessionService.listBySrcStream("device_channel")).thenReturn(List.of(cascade));
        lenient().when(broadcastSessionService.listBySrcStream("broadcast_1")).thenReturn(List.of(broadcast));
    }

    @Test
    void permitsOnlyExactActiveCascadeSessionAndInternalReaderSchema() {
        assertThat(authorizer.isAuthorized("reader-1", "server-1", "rtp", "device_channel", "rtp")).isTrue();
        assertThat(authorizer.isAuthorized(null, "server-1", "rtp", "device_channel", "rtp")).isFalse();
        assertThat(authorizer.isAuthorized("reader-1", "server-2", "rtp", "device_channel", "rtp")).isFalse();
        assertThat(authorizer.isAuthorized("reader-1", "server-1", "live", "device_channel", "rtp")).isFalse();
        assertThat(authorizer.isAuthorized("reader-1", "server-1", "rtp", "other_stream", "rtp")).isFalse();
        assertThat(authorizer.isAuthorized("reader-1", "server-1", "rtp", "device_channel", "http")).isFalse();
        assertThat(authorizer.isAuthorized("reader-1", "server-1", "rtp", "device_channel", null)).isFalse();
    }

    @Test
    void permitsExactBroadcastSessionButNotStoppedOrDeviceReceiveStream() {
        assertThat(authorizer.isAuthorized("reader-1", "server-1", "broadcast", "broadcast_1", "rtp")).isTrue();

        broadcast.setStatus(GbBroadcastSession.STATUS_STOPPED);
        assertThat(authorizer.isAuthorized("reader-1", "server-1", "broadcast", "broadcast_1", "rtp")).isFalse();
        assertThat(authorizer.isAuthorized("reader-1", "server-1", "rtp", "recv_device_channel", "rtp")).isFalse();
    }

    @Test
    void stoppedCascadeSessionIsNotAnAuthorizationBypass() {
        cascade.setStatus(CascadeSendSession.STATUS_STOPPED);

        assertThat(authorizer.isAuthorized("reader-1", "server-1", "rtp", "device_channel", "rtp")).isFalse();
    }
}
