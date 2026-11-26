package com.solo.gb28181.controller;

import com.solo.common.ServiceException;
import com.solo.config.JwtProperties;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.support.GbPlayUrlSigner;
import com.solo.media.properties.MediaProperties;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewActor;
import com.solo.media.support.ViewTokenSigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GbPlayUrlSignerTest {

    private ViewTokenSigner signer;
    private PlayResultResponse response;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        MediaProperties mediaProperties = new MediaProperties();
        mediaProperties.getPlayAuth().setTokenTtlSeconds(3600L);
        signer = new ViewTokenSigner(jwtProperties, mediaProperties);
        response = response();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void signsEveryUrlForItsOwnProtocolAndCurrentActor() {
        GbPlayUrlSigner.sign(response, ViewActor.user(7L, "tester"), signer);

        assertToken(response.getFlvUrl(), PlayTarget.httpFlv("server-1", "rtp", "device_channel"));
        assertToken(response.getWsFlvUrl(), PlayTarget.wsFlv("server-1", "rtp", "device_channel"));
        assertToken(response.getHlsUrl(), PlayTarget.hls("server-1", "rtp", "device_channel"));
        assertToken(response.getRtcUrl(), PlayTarget.rtc("server-1", "rtp", "device_channel"));
        assertThat(signer.verify(token(response.getFlvUrl()),
                PlayTarget.wsFlv("server-1", "rtp", "device_channel"))).isNull();
    }

    @Test
    void managementControllerRejectsMissingUserBeforeStartingDeviceStream() {
        GbPlayService playService = mock(GbPlayService.class);
        GbPlayController controller = new GbPlayController(playService, signer);
        RequestContextHolder.resetRequestAttributes();

        assertThatThrownBy(() -> controller.start(new PlayStartRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("播放授权身份");
        verifyNoInteractions(playService);
    }

    private void assertToken(String url, PlayTarget target) {
        ViewTokenSigner.ViewToken token = signer.verify(token(url), target);
        assertThat(token).isNotNull();
        assertThat(token.actorType()).isEqualTo(1);
        assertThat(token.actorId()).isEqualTo(7L);
    }

    static String token(String url) {
        assertThat(url).contains("wt=");
        return URLDecoder.decode(url.substring(url.indexOf("wt=") + 3), StandardCharsets.UTF_8);
    }

    static PlayResultResponse response() {
        PlayResultResponse response = new PlayResultResponse();
        response.setMediaServerId("server-1");
        response.setApp("rtp");
        response.setStreamId("device_channel");
        response.setFlvUrl("http://media/rtp/device_channel.live.flv");
        response.setWsFlvUrl("ws://media/rtp/device_channel.live.flv");
        response.setHlsUrl("http://media/rtp/device_channel/hls.m3u8");
        response.setRtcUrl("http://media/index/api/webrtc?app=rtp&stream=device_channel&type=play");
        return response;
    }
}
