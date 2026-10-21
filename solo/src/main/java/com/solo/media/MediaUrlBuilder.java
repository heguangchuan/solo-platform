package com.solo.media;

import com.solo.media.entity.MediaServer;
import org.springframework.stereotype.Component;

@Component
public class MediaUrlBuilder {

    public String buildWsFlv(MediaServer server, String app, String stream) {
        return "ws://" + ip(server) + ":" + streamPort(server) + "/" + app + "/" + stream + ".live.flv";
    }

    public String buildFlv(MediaServer server, String app, String stream) {
        return "http://" + ip(server) + ":" + streamPort(server) + "/" + app + "/" + stream + ".live.flv";
    }

    public String buildHls(MediaServer server, String app, String stream) {
        return "http://" + ip(server) + ":" + streamPort(server) + "/" + app + "/" + stream + "/hls.m3u8";
    }

    public String buildRtc(MediaServer server, String app, String stream) {
        if (server.getRtcPort() == null || server.getRtcPort() <= 0) {
            return null;
        }
        return "http://" + ip(server) + ":" + server.getRtcPort()
                + "/index/api/webrtc?app=" + app + "&stream=" + stream + "&type=play";
    }

    // ---------------- helpers ----------------

    private static String ip(MediaServer server) {
        return server.getStreamIp() == null || server.getStreamIp().isBlank()
                ? server.getHostIp() : server.getStreamIp();
    }

    private static int streamPort(MediaServer server) {
        return server.getStreamHttpPort() != null && server.getStreamHttpPort() > 0
                ? server.getStreamHttpPort() : server.getHttpPort();
    }
}
