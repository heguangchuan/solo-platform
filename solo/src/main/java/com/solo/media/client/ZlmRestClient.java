package com.solo.media.client;

import com.solo.common.ServiceException;
import com.solo.media.entity.MediaServer;
import com.solo.media.support.ViewTokenSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
@RequiredArgsConstructor
public class ZlmRestClient {

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();


    public int openRtpServer(MediaServer server, String streamId, int tcpMode, int port) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        params.put("tcp_mode", String.valueOf(tcpMode));
        params.put("port", String.valueOf(port));
        JsonNode resp = post(server, "openRtpServer", params);
        int actualPort = resp.path("port").asInt(0);
        if (actualPort <= 0) {
            throw new ServiceException("ZLM openRtpServer 未返回有效端口: " + resp);
        }
        log.info("[流媒体收流端口] 开启成功 节点:{} 流:{} 端口:{}（不按 SSRC 过滤）",
                server.getServerId(), streamId, actualPort);
        return actualPort;
    }


    public void closeRtpServer(MediaServer server, String streamId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        try {
            JsonNode resp = post(server, "closeRtpServer", params);
            boolean hit = resp.path("hit").asInt(0) == 1;
            log.info("[流媒体收流端口] 关闭结果 节点:{} 流:{} 命中:{}",
                    server.getServerId(), streamId, hit);
        } catch (ServiceException e) {
            log.warn("[流媒体收流端口] 关闭失败(忽略) 节点:{} 流:{} 错误:{}",
                    server.getServerId(), streamId, e.getMessage(), e);
        }
    }

    public boolean isMediaOnline(MediaServer server, String app, String stream) {
        return Boolean.TRUE.equals(isMediaOnlineOrNull(server, app, stream));
    }

    // true 是流在，false 是没这路流，null 是节点都连不上。启动接管时 null 别当离线清。
    public Boolean isMediaOnlineOrNull(MediaServer server, String app, String stream) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app", app);
        params.put("stream", stream);
        try {
            JsonNode resp = post(server, "getMediaList", params);
            JsonNode data = resp.path("data");
            return data.isArray() && !data.isEmpty();
        } catch (ServiceException e) {
            return null;
        }
    }


    public List<JsonNode> getMediaList(MediaServer server) {
        try {
            JsonNode resp = post(server, "getMediaList", new LinkedHashMap<>());
            JsonNode data = resp.path("data");
            if (!data.isArray()) return List.of();
            List<JsonNode> out = new ArrayList<>();
            data.forEach(out::add);
            return out;
        } catch (ServiceException e) {
            log.warn("[流媒体流列表] 查询失败 节点:{} 错误:{}", server.getServerId(), e.getMessage(), e);
            return List.of();
        }
    }


    public void closeStream(MediaServer server, String app, String stream, boolean force) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app", app);
        params.put("stream", stream);
        params.put("force", force ? "1" : "0");
        try {
            JsonNode resp = post(server, "close_streams", params);
            int count = resp.path("count_hit").asInt(0);
            log.info("[流媒体关流] 关闭成功 节点:{} 应用:{} 流:{} 命中数:{}",
                    server.getServerId(), app, stream, count);
        } catch (ServiceException e) {
            log.warn("[流媒体关流] 关闭失败 节点:{} 应用:{} 流:{} 错误:{}",
                    server.getServerId(), app, stream, e.getMessage(), e);
        }
    }

    public boolean isRtpServerAlive(MediaServer server, String streamId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        try {
            JsonNode resp = post(server, "getRtpInfo", params);
            return resp.path("exist").asBoolean(false);
        } catch (ServiceException e) {
            return false;
        }
    }


    public void pauseRtpServer(MediaServer server, String streamId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        try {
            JsonNode resp = post(server, "pauseRtpServer", params);
            log.info("[流媒体收流端口] 暂停成功 节点:{} 流:{} 命中:{}",
                    server.getServerId(), streamId, resp.path("hit").asInt(0));
        } catch (Exception e) {
            log.warn("[流媒体收流端口] 暂停失败 节点:{} 流:{} 错误:{}",
                    server.getServerId(), streamId, e.getMessage(), e);
        }
    }


    public void resumeRtpServer(MediaServer server, String streamId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        try {
            JsonNode resp = post(server, "resumeRtpServer", params);
            log.info("[流媒体收流端口] 恢复成功 节点:{} 流:{} 命中:{}",
                    server.getServerId(), streamId, resp.path("hit").asInt(0));
        } catch (Exception e) {
            log.warn("[流媒体收流端口] 恢复失败 节点:{} 流:{} 错误:{}",
                    server.getServerId(), streamId, e.getMessage(), e);
        }
    }

    // rtpType：0 自适应、1 TCP、2 UDP、3 组播；返回的 key 删除代理时要用。
    public String addStreamProxy(MediaServer server, String app, String stream, String url, int rtpType) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        params.put("url", url);
        params.put("rtp_type", String.valueOf(rtpType));
        JsonNode resp = post(server, "addStreamProxy", params);
        String key = resp.path("data").path("key").asText(null);
        if (key == null || key.isBlank()) {
            throw new ServiceException("ZLM addStreamProxy 未返回 key: " + resp);
        }
        log.info("[流媒体拉流代理] 添加成功 节点:{} 应用:{} 流:{} 传输:{} key:{}",
                server.getServerId(), app, stream, rtpType, key);
        return key;
    }


    public void delStreamProxy(MediaServer server, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", key);
        try {
            JsonNode resp = post(server, "delStreamProxy", params);
            boolean flag = resp.path("data").path("flag").asBoolean(false);
            log.info("[流媒体拉流代理] 删除结果 节点:{} key:{} 成功:{}",
                    server.getServerId(), key, flag);
        } catch (ServiceException e) {
            log.warn("[流媒体拉流代理] 删除失败(忽略) 节点:{} key:{} 错误:{}",
                    server.getServerId(), key, e.getMessage(), e);
        }
    }

    public int startSendRtp(MediaServer server, String app, String stream, String ssrc,
                            String dstIp, int dstPort, boolean udp, Integer srcPort) {
        return startSendRtp(server, app, stream, ssrc, dstIp, dstPort, udp, srcPort, 96, true, false);
    }

    // 广播裸 PCMA 用 pt=8/usePs=false/onlyAudio=true；视频默认 PS/96。
    public int startSendRtp(MediaServer server, String app, String stream, String ssrc,
                            String dstIp, int dstPort, boolean udp, Integer srcPort,
                            int pt, boolean usePs, boolean onlyAudio) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        params.put("ssrc", ssrc);
        params.put("dst_url", dstIp);
        params.put("dst_port", String.valueOf(dstPort));
        params.put("is_udp", udp ? "1" : "0");   // 注意语义：UDP=1，TCP=0
        params.put("pt", String.valueOf(pt));
        params.put("use_ps", usePs ? "1" : "0");
        if (onlyAudio) {
            params.put("only_audio", "1");
        }
        if (srcPort != null && srcPort > 0) {
            params.put("src_port", String.valueOf(srcPort));
        }
        JsonNode resp = post(server, "startSendRtp", params);
        int localPort = resp.path("local_port").asInt(0);
        log.info("[流媒体级联推流] 开始成功 节点:{} 应用:{} 流:{} SSRC:{} 目的:{}:{} UDP:{} PT:{} PS:{} 本地端口:{}",
                server.getServerId(), app, stream, ssrc, dstIp, dstPort, udp, pt, usePs, localPort);
        return localPort;
    }

    public int startSendRtpPassive(MediaServer server, String app, String stream, String ssrc) {
        return startSendRtpPassive(server, app, stream, ssrc, 96, true, false);
    }

    public int startSendRtpPassive(MediaServer server, String app, String stream, String ssrc,
                                   int pt, boolean usePs, boolean onlyAudio) {
        return startSendRtpPassive(server, app, stream, ssrc, pt, usePs, onlyAudio,
                false, null, null, 0);
    }

    // UDP 对讲用同一个 socket 收发，设备地址等它第一个上行包带来。
    // 别再拿 openRtpServer 占这个端口，两个 socket 会抢。
    public int startSendRtpPassive(MediaServer server, String app, String stream, String ssrc,
                                   int pt, boolean usePs, boolean onlyAudio,
                                   boolean udp, Integer srcPort, String recvStreamId, long closeDelayMs) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        params.put("ssrc", ssrc);
        params.put("pt", String.valueOf(pt));
        params.put("use_ps", usePs ? "1" : "0");
        // is_udp=1 走 UDP passive（kUdpPassive 单口收发）；缺省 0 为 TCP passive
        params.put("is_udp", udp ? "1" : "0");
        if (onlyAudio) {
            params.put("only_audio", "1");
        }
        if (srcPort != null && srcPort > 0) {
            params.put("src_port", String.valueOf(srcPort));
        }
        if (recvStreamId != null && !recvStreamId.isBlank()) {
            params.put("recv_stream_id", recvStreamId);
        }
        if (closeDelayMs > 0) {
            params.put("close_delay_ms", String.valueOf(closeDelayMs));
        }
        JsonNode resp = post(server, "startSendRtpPassive", params);
        int localPort = resp.path("local_port").asInt(0);
        if (localPort <= 0) {
            throw new ServiceException("ZLM startSendRtpPassive 未返回有效端口: " + resp);
        }
        log.info("[流媒体级联推流] 被动开始成功 节点:{} 应用:{} 流:{} SSRC:{} PT:{} PS:{} UDP:{} 本地端口:{} 收流:{}",
                server.getServerId(), app, stream, ssrc, pt, usePs, udp, localPort, recvStreamId);
        return localPort;
    }

    public void stopSendRtp(MediaServer server, String app, String stream, String ssrc) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        if (ssrc != null && !ssrc.isBlank()) {
            params.put("ssrc", ssrc);
        }
        try {
            post(server, "stopSendRtp", params);
            log.info("[流媒体级联推流] 停止成功 节点:{} 应用:{} 流:{} SSRC:{}",
                    server.getServerId(), app, stream, ssrc);
        } catch (ServiceException e) {
            // 源流先没了时 ZLM 会回 -500；停止本来就幂等，记 INFO 就行
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("code=-500") && msg.contains("can not find the stream")) {
                log.info("[流媒体级联推流] 发送器已不存在，无需停止 节点:{} 应用:{} 流:{}",
                        server.getServerId(), app, stream);
            } else {
                log.warn("[流媒体级联推流] 停止失败(忽略) 节点:{} 应用:{} 流:{} 错误:{}",
                        server.getServerId(), app, stream, msg, e);
            }
        }
    }

    public String exchangeWebRtcSdp(MediaServer server, String app, String stream, String type, String offerSdp) {
        return exchangeWebRtcSdp(server, app, stream, type, offerSdp, null);
    }

    public String exchangeWebRtcSdp(MediaServer server, String app, String stream, String type,
                                    String offerSdp, String playToken) {
        boolean play = "play".equalsIgnoreCase(type);
        if (play && (playToken == null || playToken.isBlank())) {
            throw new IllegalArgumentException("WebRTC 播放 SDP 交换必须携带播放令牌");
        }
        String sdp = "push".equalsIgnoreCase(type) ? forceG711Audio(offerSdp) : offerSdp;
        // 该版本 ZLM 的 webrtc 接口只认 URL query 上的 secret/type/app/stream，SDP 必须作为原始 body；
        // 用 form 表单提交会报 -300 "Required parameter missed: type"
        String url = "http://" + server.getHostIp() + ":" + server.getHttpPort() + "/index/api/webrtc"
                + "?secret=" + URLEncoder.encode(server.getSecret() == null ? "" : server.getSecret(), StandardCharsets.UTF_8)
                + "&type=" + URLEncoder.encode(type, StandardCharsets.UTF_8)
                + "&app=" + URLEncoder.encode(app, StandardCharsets.UTF_8)
                + "&stream=" + URLEncoder.encode(stream, StandardCharsets.UTF_8);
        if (play) {
            url += "&" + ViewTokenSigner.PARAM + "="
                    + URLEncoder.encode(playToken, StandardCharsets.UTF_8);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "text/plain;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(sdp, StandardCharsets.UTF_8))
                .build();
        JsonNode resp;
        try {
            HttpResponse<String> httpResp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResp.statusCode() != 200) {
                throw new ServiceException("ZLM HTTP " + httpResp.statusCode() + " webrtc");
            }
            resp = objectMapper.readTree(httpResp.body());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("ZLM webrtc 调用异常: " + e.getClass().getSimpleName());
        }
        int code = resp.path("code").asInt(-1);
        if (code != 0) {
            throw new ServiceException("ZLM webrtc 失败 code=" + code + " msg=" + resp.path("msg").asText());
        }
        String answer = resp.path("sdp").asText("");
        if (answer.isBlank()) {
            throw new ServiceException("ZLM webrtc 未返回 answer SDP: " + resp);
        }
        return answer;
    }

    // ZLM 不会把 Opus 转成设备要的 G.711，不裁会协商成 Opus，设备收到也放不出声。
    // 有 PCMA 就只留 PCMA；同时留 PCMU 的话，ZLM 可能偏选 PCMU。
    private String forceG711Audio(String sdp) {
        if (sdp == null || sdp.isBlank()) {
            return sdp;
        }
        String[] lines = sdp.split("\r\n|\n", -1);
        Map<String, String> encByPt = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.startsWith("a=rtpmap:")) {
                int sp = line.indexOf(' ');
                if (sp <= 0) {
                    continue;
                }
                String pt = line.substring("a=rtpmap:".length(), sp).trim();
                String enc = line.substring(sp + 1).trim().split("/")[0].toUpperCase();
                encByPt.put(pt, enc);
            }
        }
        List<String> keepPts = new ArrayList<>();
        for (Map.Entry<String, String> e : encByPt.entrySet()) {
            if ("PCMA".equals(e.getValue()) && !keepPts.contains(e.getKey())) {
                keepPts.add(e.getKey());
            }
        }
        boolean pcma = !keepPts.isEmpty();
        if (!pcma) {
            for (Map.Entry<String, String> e : encByPt.entrySet()) {
                if ("PCMU".equals(e.getValue()) && !keepPts.contains(e.getKey())) {
                    keepPts.add(e.getKey());
                }
            }
        }
        if (keepPts.isEmpty()) {
            log.warn("[WebRTC推流] offer 无 PCMA/PCMU 载荷，无法强制 G.711，原样转发");
            return sdp;
        }
        if (!pcma) {
            log.warn("[WebRTC推流] offer 无 PCMA，退而保留 PCMU，设备腿按 a-law 收可能仍无声");
        }

        StringBuilder out = new StringBuilder(sdp.length());
        boolean inAudio = false;
        for (String line : lines) {
            if (line.startsWith("m=")) {
                inAudio = line.startsWith("m=audio");
                if (inAudio) {
                    String[] head = line.split("\\s+", 3);
                    if (head.length >= 3) {
                        String proto = head[2].split("\\s+")[0];
                        out.append(head[0]).append(' ').append(head[1]).append(' ').append(proto);
                        for (String pt : keepPts) {
                            out.append(' ').append(pt);
                        }
                        out.append("\r\n");
                        continue;
                    }
                }
                out.append(line).append("\r\n");
                continue;
            }
            // 音频段内丢弃被删载荷的 rtpmap/fmtp/rtcp-fb（rtcp-fb 通配 * 保留）
            if (inAudio && (line.startsWith("a=rtpmap:") || line.startsWith("a=fmtp:")
                    || line.startsWith("a=rtcp-fb:"))) {
                int sp = line.indexOf(' ');
                String pt = line.substring(line.indexOf(':') + 1, sp > 0 ? sp : line.length()).trim();
                if (!"*".equals(pt) && !keepPts.contains(pt)) {
                    continue;
                }
            }
            out.append(line).append("\r\n");
        }
        return out.toString();
    }


    // 这是打开录制能力，不是马上产出文件；流结束时 ZLM 自己收尾并回调 on_record_mp4。
    public boolean startRecord(MediaServer server, String app, String stream, String period, int maxSecond) {
        Map<String, String> params = new LinkedHashMap<>();
        // 录制类型：0=hls、1=mp4
        params.put("type", "1");
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        params.put("period", period);
        params.put("max_second", String.valueOf(maxSecond));
        try {
            post(server, "startRecord", params);
            log.info("[流媒体录制] 开启成功 节点:{} 应用:{} 流:{} 周期:{} 最长:{}s",
                    server.getServerId(), app, stream, period, maxSecond);
            return true;
        } catch (ServiceException e) {
            log.warn("[流媒体录制] 开启失败 节点:{} 应用:{} 流:{} 原因:{}",
                    server.getServerId(), app, stream, e.getMessage());
            return false;
        }
    }

    // UNAVAILABLE 只是没问到，可能是接口抖动，别直接当成没在录就补发。
    public RecordState isRecording(MediaServer server, String app, String stream) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("type", "1");
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        try {
            JsonNode resp = post(server, "isRecording", params);
            return resp.path("status").asBoolean(false)
                    ? RecordState.RECORDING : RecordState.IDLE;
        } catch (ServiceException e) {
            return RecordState.UNAVAILABLE;
        }
    }

    public enum RecordState {
        RECORDING, IDLE, UNAVAILABLE
    }

    public List<String> getMp4Record(MediaServer server, String app, String stream, String period) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        params.put("period", period);
        try {
            JsonNode resp = post(server, "getMp4Record", params);
            JsonNode arr = resp.path("data");
            if (arr == null || !arr.isArray()) {
                return List.of();
            }
            List<String> paths = new ArrayList<>(arr.size());
            for (JsonNode item : arr) {
                String path = item.path("path").asText(null);
                if (path != null && !path.isBlank()) {
                    paths.add(path);
                }
            }
            return paths;
        } catch (ServiceException e) {
            log.warn("[流媒体录像文件] 查询失败 节点:{} 应用:{} 流:{} 周期:{} 原因:{}",
                    server.getServerId(), app, stream, period, e.getMessage());
            return List.of();
        }
    }

    public boolean setServerConfig(MediaServer server, Map<String, String> params) {
        try {
            post(server, "setServerConfig", params);
            log.info("[流媒体节点配置] 下发成功 节点:{} 项数:{}", server.getServerId(), params.size());
            return true;
        } catch (ServiceException e) {
            log.warn("[流媒体节点配置] 下发失败 节点:{} 错误:{}", server.getServerId(), e.getMessage(), e);
            return false;
        }
    }


    public boolean ping(MediaServer server) {
        try {
            post(server, "version", new LinkedHashMap<>());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // on_record_mp4 给的 url 经常是不带斜杠的相对路径，直接喂 HttpClient 会报没有 scheme。
    public static String resolveDownloadUrl(MediaServer server, String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String path = url.startsWith("/") ? url : "/" + url;
        return "http://" + server.getHostIp() + ":" + server.getHttpPort() + path;
    }

    private JsonNode post(MediaServer server, String method, Map<String, String> params) {
        String url = "http://" + server.getHostIp() + ":" + server.getHttpPort() + "/index/api/" + method;
        Map<String, String> merged = new LinkedHashMap<>(params);
        merged.put("secret", server.getSecret() == null ? "" : server.getSecret());
        String body = urlEncodeForm(merged);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new ServiceException("ZLM HTTP " + resp.statusCode() + " " + method);
            }
            JsonNode node = objectMapper.readTree(resp.body());
            int code = node.path("code").asInt(-1);
            if (code != 0) {
                throw new ServiceException("ZLM " + method + " 失败 code=" + code + " msg=" + node.path("msg").asText());
            }
            return node;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            // 有些连接异常 getMessage() 是 null；健康探测调用勤，日志别刷整段堆栈
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            log.warn("[流媒体] 调用异常 方法:{} URL:{} 错误:{}", method, url, detail);
            throw new ServiceException("ZLM 调用异常: " + method + " @ " + url + " - " + detail);
        }
    }

    private static String urlEncodeForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
