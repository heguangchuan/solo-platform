package com.solo.gb28181.media;

import com.solo.gb28181.entity.GbMediaServer;
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

    public String buildWsFlv(GbMediaServer server, String app, String stream) {
        return "ws://" + ip(server) + ":" + streamPort(server) + "/" + app + "/" + stream + ".live.flv";
    }

    public String buildFlv(GbMediaServer server, String app, String stream) {
        return "http://" + ip(server) + ":" + streamPort(server) + "/" + app + "/" + stream + ".live.flv";
    }

    public String buildHls(GbMediaServer server, String app, String stream) {
        return "http://" + ip(server) + ":" + streamPort(server) + "/" + app + "/" + stream + "/hls.m3u8";
    }

    public String buildRtc(GbMediaServer server, String app, String stream) {
        if (server.getRtcPort() == null || server.getRtcPort() <= 0) {
            return null;
        }
        return "http://" + ip(server) + ":" + server.getRtcPort()
                + "/index/api/webrtc?app=" + app + "&stream=" + stream + "&type=play";
    }

    /**
     * 把 ZLM {@code getSnap} 返回的本地文件路径转成可访问 URL
     * <p>ZLM 会把截图放在 {@code <www>/snap/...} 目录，HTTP 服务能直接访问；
     * 这里假定 ZLM 已经把它的 www 暴露在 streamHttpPort（与 HTTP-FLV 同端口）下。
     * snapLocalPath 形如 {@code /opt/media/bin/www/snap/abcd.jpg}，
     * 我们截取 {@code /snap/abcd.jpg} 拼上 host:port 即可。</p>
     */
    public String buildSnapAccessUrl(GbMediaServer server, String snapLocalPath) {
        if (snapLocalPath == null || snapLocalPath.isBlank()) {
            return null;
        }
        int idx = snapLocalPath.indexOf("/snap/");
        String relative = idx >= 0 ? snapLocalPath.substring(idx) : ("/" + snapLocalPath);
        return "http://" + ip(server) + ":" + streamPort(server) + relative;
    }

    // ---------------- helpers ----------------

    private static String ip(GbMediaServer server) {
        return server.getStreamIp() == null || server.getStreamIp().isBlank()
                ? server.getHostIp() : server.getStreamIp();
    }

    private static int streamPort(GbMediaServer server) {
        return server.getStreamHttpPort() != null && server.getStreamHttpPort() > 0
                ? server.getStreamHttpPort() : server.getHttpPort();
    }
}
