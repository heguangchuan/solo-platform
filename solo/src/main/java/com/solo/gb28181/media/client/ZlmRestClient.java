package com.solo.gb28181.media.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbMediaServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ZLMediaKit REST API 客户端
 * <p>ZLM 接口约定（form-data，secret 走 query 或 body 均可，这里统一拼到 body）：
 * {@code POST http://<host>:<httpPort>/index/api/<method>?secret=xxx&k=v...}</p>
 * <p>所有调用都走 form-urlencoded body；响应统一 JSON：{@code {"code":0,...}}，
 * code != 0 抛 {@link ServiceException}。</p>
 *
 * <h3>覆盖的接口（S2.1）</h3>
 * <ul>
 *     <li>{@code openRtpServer}    — 申请 RTP 收流端口</li>
 *     <li>{@code closeRtpServer}   — 关闭 RTP 收流端口</li>
 *     <li>{@code getMediaList}     — 查询流是否还活着</li>
 *     <li>{@code getRtpInfo}       — 查询 RTP 端口状态（保活）</li>
 *     <li>{@code getSnap}          — 流截图</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZlmRestClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ---------------- 公共 API ----------------

    /**
     * 打开一个 RTP 收流端口
     *
     * @param server   节点
     * @param streamId ZLM stream 名（约定 app=rtp、stream=<deviceId>_<channelId>，传完整 stream 部分即可）
     * @param ssrc     10 位十进制 SSRC
     * @param tcpMode  0=UDP，1=TCP-passive，2=TCP-active
     * @param port     0=自动分配（推荐），>0=指定端口
     * @return ZLM 实际分配的端口
     */
    public int openRtpServer(GbMediaServer server, String streamId, String ssrc, int tcpMode, int port) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        params.put("ssrc", ssrc);
        params.put("tcp_mode", String.valueOf(tcpMode));
        params.put("port", String.valueOf(port));
        JsonNode resp = post(server, "openRtpServer", params);
        int actualPort = resp.path("port").asInt(0);
        if (actualPort <= 0) {
            throw new ServiceException("ZLM openRtpServer 未返回有效端口: " + resp);
        }
        log.info("[流媒体] openRtpServer ok server={} stream={} ssrc={} port={}",
                server.getServerId(), streamId, ssrc, actualPort);
        return actualPort;
    }

    /**
     * 关闭 RTP 收流端口
     *
     * @return true=确实关闭了；false=已经不存在
     */
    public boolean closeRtpServer(GbMediaServer server, String streamId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        try {
            JsonNode resp = post(server, "closeRtpServer", params);
            boolean hit = resp.path("hit").asInt(0) == 1;
            log.info("[流媒体] closeRtpServer server={} stream={} hit={}",
                    server.getServerId(), streamId, hit);
            return hit;
        } catch (ServiceException e) {
            log.warn("[流媒体] closeRtpServer 失败（忽略） server={} stream={} err={}",
                    server.getServerId(), streamId, e.getMessage());
            return false;
        }
    }

    /**
     * 流是否存在
     */
    public boolean isMediaOnline(GbMediaServer server, String app, String stream) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app", app);
        params.put("stream", stream);
        try {
            JsonNode resp = post(server, "getMediaList", params);
            JsonNode data = resp.path("data");
            return data.isArray() && !data.isEmpty();
        } catch (ServiceException e) {
            return false;
        }
    }

    /**
     * RTP 端口保活查询
     */
    public boolean isRtpServerAlive(GbMediaServer server, String streamId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        try {
            JsonNode resp = post(server, "getRtpInfo", params);
            return resp.path("exist").asBoolean(false);
        } catch (ServiceException e) {
            return false;
        }
    }

    /**
     * 截图（ZLM 同步抓帧）
     *
     * @param sourceUrl    要截图的播放地址（一般是 rtmp/ws-flv 等 ZLM 已有协议路径）
     * @param timeoutSec   超时秒
     * @param expireSec    缓存秒（同 url 多次请求复用）
     * @return 服务器本地 jpg 文件路径，前端通过 ZLM HTTP 静态服务访问
     */
    public String getSnap(GbMediaServer server, String sourceUrl, int timeoutSec, int expireSec) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("url", sourceUrl);
        params.put("timeout_sec", String.valueOf(timeoutSec));
        params.put("expire_sec", String.valueOf(expireSec));
        JsonNode resp = post(server, "getSnap", params);
        return resp.path("data").asText();
    }

    // ---------------- 底层 POST ----------------

    private JsonNode post(GbMediaServer server, String method, Map<String, String> params) {
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
            JsonNode node = MAPPER.readTree(resp.body());
            int code = node.path("code").asInt(-1);
            if (code != 0) {
                throw new ServiceException("ZLM " + method + " 失败 code=" + code + " msg=" + node.path("msg").asText());
            }
            return node;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("ZLM 调用异常: " + method + " - " + e.getMessage());
        }
    }

    private static String urlEncodeForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
