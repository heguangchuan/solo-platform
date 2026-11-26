package com.solo.openapi.controller;

import com.solo.config.JwtProperties;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.PlayStartRequest;
import com.solo.gb28181.request.PlaybackStartRequest;
import com.solo.gb28181.response.PlayResultResponse;
import com.solo.gb28181.response.PlaybackResultResponse;
import com.solo.gb28181.service.GbPlayService;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.media.properties.MediaProperties;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewTokenSigner;
import com.solo.openapi.auth.OpenAppContext;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.entity.OpenApp;
import com.solo.openapi.exception.OpenApiException;
import com.solo.openapi.service.OpenCatalogService;
import com.solo.openapi.web.OpenApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenPlayControllerTest {

    private GbPlayService playService;
    private GbPlaybackService playbackService;
    private OpenCatalogService catalogService;
    private ViewTokenSigner signer;

    @BeforeEach
    void setUp() {
        playService = mock(GbPlayService.class);
        playbackService = mock(GbPlaybackService.class);
        catalogService = mock(OpenCatalogService.class);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        MediaProperties mediaProperties = new MediaProperties();
        mediaProperties.getPlayAuth().setTokenTtlSeconds(3600L);
        signer = new ViewTokenSigner(jwtProperties, mediaProperties);
    }

    @Test
    void openLiveUrlsUseApplicationActorAndBoundTargets() {
        OpenPlayController controller = new OpenPlayController(playService, catalogService, signer);
        MockHttpServletRequest request = openRequest();
        when(catalogService.resolveChannel("device", "channel")).thenReturn(channel());
        when(playService.start(any())).thenReturn(playResponse());
        PlayStartRequest start = new PlayStartRequest();
        start.setDeviceId("device");
        start.setChannelId("channel");

        OpenApiResult<PlayResultResponse> result = controller.start(start, request);

        ViewTokenSigner.ViewToken token = signer.verify(token(result.getData().getFlvUrl()),
                PlayTarget.httpFlv("server-1", "rtp", "device_channel"));
        assertThat(token).isNotNull();
        assertThat(token.actorType()).isEqualTo(2);
        assertThat(token.actorId()).isEqualTo(99L);
        assertThat(token.actorName()).isEqualTo("integration-app");
        assertThat(signer.verify(token(result.getData().getRtcUrl()),
                PlayTarget.rtc("server-1", "rtp", "device_channel"))).isNotNull();
    }

    @Test
    void openPlaybackAlsoUsesApplicationActor() {
        OpenPlaybackController controller = new OpenPlaybackController(playbackService, catalogService, signer);
        MockHttpServletRequest request = openRequest();
        when(catalogService.resolveChannel("device", "channel")).thenReturn(channel());
        PlaybackResultResponse response = new PlaybackResultResponse();
        copyPlayFields(response);
        when(playbackService.start(any())).thenReturn(response);
        PlaybackStartRequest start = new PlaybackStartRequest();
        start.setDeviceId("device");
        start.setChannelId("channel");

        OpenApiResult<PlaybackResultResponse> result = controller.start(start, request);

        ViewTokenSigner.ViewToken token = signer.verify(token(result.getData().getHlsUrl()),
                PlayTarget.hls("server-1", "rtp", "device_channel"));
        assertThat(token).isNotNull();
        assertThat(token.actorType()).isEqualTo(2);
        assertThat(token.actorId()).isEqualTo(99L);
    }

    @Test
    void missingOpenApplicationContextFailsBeforeResolvingOrStartingStream() {
        OpenPlayController controller = new OpenPlayController(playService, catalogService, signer);
        PlayStartRequest start = new PlayStartRequest();
        start.setDeviceId("device");
        start.setChannelId("channel");

        assertThatThrownBy(() -> controller.start(start, new MockHttpServletRequest()))
                .isInstanceOf(OpenApiException.class)
                .hasMessageContaining("播放授权身份");
        verifyNoInteractions(playService, catalogService);
    }

    private static MockHttpServletRequest openRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(OpenApiConstants.ATTR_APP_CONTEXT,
                new OpenAppContext(99L, "app-key", "integration-app", Set.of("gb:play:write"), new OpenApp()));
        return request;
    }

    private static GbDeviceChannel channel() {
        GbDeviceChannel channel = new GbDeviceChannel();
        channel.setDeviceId("device");
        channel.setChannelId("channel");
        return channel;
    }

    private static PlayResultResponse playResponse() {
        PlayResultResponse response = new PlayResultResponse();
        copyPlayFields(response);
        return response;
    }

    private static void copyPlayFields(PlayResultResponse response) {
        response.setMediaServerId("server-1");
        response.setApp("rtp");
        response.setStreamId("device_channel");
        response.setFlvUrl("http://media/rtp/device_channel.live.flv");
        response.setWsFlvUrl("ws://media/rtp/device_channel.live.flv");
        response.setHlsUrl("http://media/rtp/device_channel/hls.m3u8");
        response.setRtcUrl("http://media/index/api/webrtc?app=rtp&stream=device_channel&type=play");
    }

    private static String token(String url) {
        return URLDecoder.decode(url.substring(url.indexOf("wt=") + 3), StandardCharsets.UTF_8);
    }
}
