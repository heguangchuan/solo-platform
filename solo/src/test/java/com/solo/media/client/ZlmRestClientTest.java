package com.solo.media.client;

import com.solo.media.entity.MediaServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ZlmRestClientTest {

    @Test
    void webrtcPlayWithoutTokenFailsBeforeCallingMediaServer() {
        ZlmRestClient client = new ZlmRestClient(mock(ObjectMapper.class));
        MediaServer server = new MediaServer();
        server.setHostIp("127.0.0.1");
        server.setHttpPort(1);
        server.setSecret("secret");

        assertThatThrownBy(() -> client.exchangeWebRtcSdp(server, "rtp", "recv_1", "play", "offer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("播放令牌");
        assertThatThrownBy(() -> client.exchangeWebRtcSdp(server, "rtp", "recv_1", "play", "offer", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("播放令牌");
    }
}
