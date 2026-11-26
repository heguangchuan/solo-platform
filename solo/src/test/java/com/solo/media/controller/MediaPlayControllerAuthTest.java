package com.solo.media.controller;

import com.solo.config.JwtProperties;
import com.solo.media.properties.MediaProperties;
import com.solo.media.response.StreamProxyPlayUrlResponse;
import com.solo.media.response.StreamPushPlayUrlResponse;
import com.solo.media.service.StreamProxyService;
import com.solo.media.service.StreamPushService;
import com.solo.media.support.PlayTarget;
import com.solo.media.support.ViewTokenSigner;
import com.solo.onvif.controller.OnvifPlayController;
import com.solo.onvif.request.OnvifPlayStartRequest;
import com.solo.onvif.response.OnvifPlayResultResponse;
import com.solo.onvif.service.OnvifPlayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaPlayControllerAuthTest {

    private ViewTokenSigner signer;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        MediaProperties mediaProperties = new MediaProperties();
        mediaProperties.getPlayAuth().setTokenTtlSeconds(3600L);
        signer = new ViewTokenSigner(jwtProperties, mediaProperties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserId", 7L);
        request.setAttribute("currentUsername", "tester");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void onvifStartReturnsOnlySignedUrls() {
        OnvifPlayService service = mock(OnvifPlayService.class);
        OnvifPlayResultResponse response = new OnvifPlayResultResponse();
        response.setMediaServerId("server-1");
        response.setApp("rtsp");
        response.setStreamId("onvif_device");
        response.setFlvUrl("http://media/rtsp/onvif_device.live.flv");
        response.setWsFlvUrl("ws://media/rtsp/onvif_device.live.flv");
        response.setHlsUrl("http://media/rtsp/onvif_device/hls.m3u8");
        response.setRtcUrl(null);
        when(service.start(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        OnvifPlayResultResponse result = new OnvifPlayController(service, signer).start(new OnvifPlayStartRequest()).getData();

        assertToken(result.getFlvUrl(), PlayTarget.httpFlv("server-1", "rtsp", "onvif_device"));
        assertToken(result.getWsFlvUrl(), PlayTarget.wsFlv("server-1", "rtsp", "onvif_device"));
        assertToken(result.getHlsUrl(), PlayTarget.hls("server-1", "rtsp", "onvif_device"));
        assertThat(result.getRtcUrl()).isNull();
    }

    @Test
    void streamProxyUrlsAreSignedForStoredAppAndStream() {
        StreamProxyService service = mock(StreamProxyService.class);
        StreamProxyPlayUrlResponse response = new StreamProxyPlayUrlResponse();
        response.setMediaServerId("server-1");
        response.setApp("custom-proxy");
        response.setStream("camera-1");
        response.setFlvUrl("http://media/custom-proxy/camera-1.live.flv");
        response.setWsFlvUrl("ws://media/custom-proxy/camera-1.live.flv");
        response.setHlsUrl("http://media/custom-proxy/camera-1/hls.m3u8");
        response.setRtcUrl("http://media/index/api/webrtc?app=custom-proxy&stream=camera-1&type=play");
        when(service.getPlayUrls(11L)).thenReturn(response);

        StreamProxyPlayUrlResponse result = new StreamProxyController(service, signer).playUrls(11L).getData();

        assertToken(result.getFlvUrl(), PlayTarget.httpFlv("server-1", "custom-proxy", "camera-1"));
        assertToken(result.getWsFlvUrl(), PlayTarget.wsFlv("server-1", "custom-proxy", "camera-1"));
        assertToken(result.getHlsUrl(), PlayTarget.hls("server-1", "custom-proxy", "camera-1"));
        assertToken(result.getRtcUrl(), PlayTarget.rtc("server-1", "custom-proxy", "camera-1"));
    }

    @Test
    void streamPushUrlsAreSignedIndependentlyOfPushCredential() {
        StreamPushService service = mock(StreamPushService.class);
        StreamPushPlayUrlResponse response = new StreamPushPlayUrlResponse();
        response.setMediaServerId("server-1");
        response.setApp("live");
        response.setStream("external-1");
        response.setFlvUrl("http://media/live/external-1.live.flv");
        response.setWsFlvUrl("ws://media/live/external-1.live.flv");
        response.setHlsUrl("http://media/live/external-1/hls.m3u8");
        response.setRtcUrl("http://media/index/api/webrtc?app=live&stream=external-1&type=play");
        when(service.getPlayUrls(12L)).thenReturn(response);

        StreamPushPlayUrlResponse result = new StreamPushController(service, signer).playUrls(12L).getData();

        assertToken(result.getFlvUrl(), PlayTarget.httpFlv("server-1", "live", "external-1"));
        assertToken(result.getWsFlvUrl(), PlayTarget.wsFlv("server-1", "live", "external-1"));
        assertToken(result.getHlsUrl(), PlayTarget.hls("server-1", "live", "external-1"));
        assertToken(result.getRtcUrl(), PlayTarget.rtc("server-1", "live", "external-1"));
    }

    private void assertToken(String url, PlayTarget target) {
        String token = URLDecoder.decode(url.substring(url.indexOf("wt=") + 3), StandardCharsets.UTF_8);
        ViewTokenSigner.ViewToken verified = signer.verify(token, target);
        assertThat(verified).isNotNull();
        assertThat(verified.actorType()).isEqualTo(1);
        assertThat(verified.actorId()).isEqualTo(7L);
    }
}
