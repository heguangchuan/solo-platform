package com.solo.media;

import com.solo.media.entity.MediaServer;
import org.springframework.stereotype.Component;

/**
 * ZLM 各种播放协议 URL 拼装
 * <p>所有方法都是纯函数（无 IO），由 PlayService 在拼装 {@code PlayResultVO} 时调用。</p>
 *
 * <h3>地址约定</h3>
 * <ul>
 *   <li>对外播放 IP = {@code streamIp != null ? streamIp : hostIp}</li>
 *   <li>HTTP/WS-FLV/HLS 端口 = {@code streamHttpPort > 0 ? streamHttpPort : httpPort}</li>
 *   <li>WS-FLV：{@code ws://<ip>:<port>/<app>/<stream>.live.flv}</li>
 *   <li>HTTP-FLV：{@code http://<ip>:<port>/<app>/<stream>.live.flv}</li>
 *   <li>HLS：{@code http://<ip>:<port>/<app>/<stream>/hls.m3u8}</li>
 *   <li>WebRTC（节点未配 rtcPort 时返回 null）：{@code http://<ip>:<rtcPort>/index/api/webrtc?app=<app>&stream=<stream>&type=play}</li>
 * </ul>
 */
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
