package com.solo.media.client;

import com.solo.common.ServiceException;
import com.solo.media.entity.MediaServer;
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

    /**
     * 统一使用 Spring Boot 4 自动配置的 Jackson 3 实例解析 ZLM 响应
     */
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

    /**
     * true=流在，false=节点连上了但没这路流，null=节点连不上，不知道。
     * 启动接管靠它区分能不能清，拿到 null 就按流还在处理。
     */
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

    /**
     * 让 ZLM 从一个 RTSP/RTMP 源拉流，产出 app/stream 名下的可分发流。
     * ONVIF 点播走的就是这个：源是 rtsp://user:pwd@host:port/path，拉起来后 ZLM 暴露
     * HTTP-FLV / HLS / WS-FLV / WebRTC。
     *
     * @param server  节点
     * @param app     app 名（ONVIF 约定 {@code rtsp}，与国标 GB 用的 {@code rtp} 区分）
     * @param stream  stream 名（ONVIF 约定 {@code onvif_<deviceId>}）
     * @param url     源 URL（含账密）
     * @param rtpType RTSP 传输：0=TCP+UDP 自适应，1=TCP，2=UDP，3=组播
     * @return ZLM 分配的代理 key（addStreamProxy.data.key），用于 delStreamProxy
     */
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

    /**
     * 把 ZLM 里的一路流按 PS/RTP 主动推到指定目的地址（UDP 或 TCP 主动去连对端）。
     * 本平台作为下级被上级点播时用：先向真实设备取流拿到 app=rtp/stream，
     * 再用这个方法转推给上级的 dstIp:dstPort。
     *
     * @param server  流所在 ZLM 节点
     * @param app     源流 app（国标取流固定 {@code rtp}）
     * @param stream  源流 stream 名
     * @param ssrc    RTP 头 SSRC（10 位十进制，取上级 SDP 的 {@code y=} 值）
     * @param dstIp   上级收流 IP（{@code c=} 行）
     * @param dstPort 上级收流端口（{@code m=} 行）
     * @param udp     true=UDP，false=TCP 主动（本平台去连上级）
     * @param srcPort 本地发送端口（可空；null=ZLM 自行选）
     * @return ZLM 实际使用的本地发送端口（无则返回 0）
     */
    public int startSendRtp(MediaServer server, String app, String stream, String ssrc,
                            String dstIp, int dstPort, boolean udp, Integer srcPort) {
        // 级联视频默认 PS/96
        return startSendRtp(server, app, stream, ssrc, dstIp, dstPort, udp, srcPort, 96, true, false);
    }

    /**
     * 同上，可指定载荷：广播用 pt=8/usePs=false/onlyAudio=true 发裸 PCMA，pt=96/usePs=true 发 PS。
     */
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

    /**
     * 开一个监听端口等对端来连后再推流（TCP 主动模式：上级主动来连本平台的端口）。
     * 回给上级 200 OK 之后马上调，上级会按 SDP 里返回的端口来建立 TCP 连接。
     *
     * @return ZLM 实际监听的本地端口（供本平台写进回复 SDP 的 {@code m=} 行）
     */
    public int startSendRtpPassive(MediaServer server, String app, String stream, String ssrc) {
        // 级联视频默认 PS/96
        return startSendRtpPassive(server, app, stream, ssrc, 96, true, false);
    }

    /**
     * 同上，可指定载荷（广播裸 PCMA 用 pt=8/usePs=false/onlyAudio=true）。
     */
    public int startSendRtpPassive(MediaServer server, String app, String stream, String ssrc,
                                   int pt, boolean usePs, boolean onlyAudio) {
        // 默认 TCP 被动（级联/TCP-active 设备场景）
        return startSendRtpPassive(server, app, stream, ssrc, pt, usePs, onlyAudio,
                false, null, null, 0);
    }

    /**
     * 被动推流完整版。
     * UDP 对讲是对称 RTP：传 udp=true + srcPort + recvStreamId，ZLM 只开一个 UDP socket，
     * 同一端口既收设备上行（注册成 app=rtp/stream=recvStreamId 的流），又把本端音频下发给设备。
     * 对端地址靠设备第一个上行 RTP 包学到，不用 dst_url/dst_port。
     * 别再用 openRtpServer 占同一个端口，两个 socket 会抢端口报 address already in use。
     *
     * @param udp          true=UDP 被动（单口收发，对讲用），false=TCP 被动监听
     * @param srcPort      本地端口（UDP 对讲从级联端口段取；null=ZLM 随机）
     * @param recvStreamId 同 socket 收到的上行流注册名；非空时启用同口收流
     * @param closeDelayMs 无对端连接时端口保留毫秒；0 用 ZLM 默认
     * @return ZLM 实际监听/绑定的本地端口（写进应答 SDP 的 m= 行）
     */
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

    /**
     *
     */
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
            // 浏览器先断开导致源流已注销、发送器随之销毁，或本就没建成功时，ZLM 回 -500 can not find the stream。
            // 停止是幂等操作，目标状态已达成，按正常情况记一行即可，不打堆栈
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

    /**
     * WebRTC SDP 交换（浏览器 WHIP 式推流/拉流）。对应 ZLM /index/api/webrtc。
     * 浏览器把 offer SDP 交给平台，平台带着 secret 转发给 ZLM（浏览器不该持有 secret，
     * 直连 rtcPort 还有跨域问题），再把 answer SDP 原样还回去。
     *
     * @param app       应用名（广播配 broadcast）
     * @param stream    流名（广播流ID）
     * @param type      push=推流 / play=拉流
     * @param offerSdp  浏览器生成的 offer SDP
     * @return ZLM 应答的 answer SDP
     */
    public String exchangeWebRtcSdp(MediaServer server, String app, String stream, String type, String offerSdp) {
        // 推流侧把 offer 音频裁成 G.711，原因见 forceG711Audio
        String sdp = "push".equalsIgnoreCase(type) ? forceG711Audio(offerSdp) : offerSdp;
        // 该版本 ZLM 的 webrtc 接口只认 URL query 上的 secret/type/app/stream，SDP 必须作为原始 body；
        // 用 form 表单提交会报 -300 "Required parameter missed: type"
        String url = "http://" + server.getHostIp() + ":" + server.getHttpPort() + "/index/api/webrtc"
                + "?secret=" + URLEncoder.encode(server.getSecret() == null ? "" : server.getSecret(), StandardCharsets.UTF_8)
                + "&type=" + URLEncoder.encode(type, StandardCharsets.UTF_8)
                + "&app=" + URLEncoder.encode(app, StandardCharsets.UTF_8)
                + "&stream=" + URLEncoder.encode(stream, StandardCharsets.UTF_8);
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

    /**
     * 把 WebRTC 推流 offer 的音频段裁成只剩 G.711（优先 PCMA/PT8，没有 PCMA 才退 PCMU）。
     * 浏览器默认 offer Opus，ZLM 不做 Opus → G.711 转码，设备腿又按裸 PCMA 收，不裁会协商成 Opus，
     * 设备收得到包但放不出声。PCMA 和 PCMU 都留着也不行，ZLM 可能选 PCMU，所以有 PCMA 时只留 8。
     * 连 G.711 都没有就原样返回。
     */
    private String forceG711Audio(String sdp) {
        if (sdp == null || sdp.isBlank()) {
            return sdp;
        }
        String[] lines = sdp.split("\r\n|\n", -1);
        // 载荷号 -> 编码名
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
        // 有 PCMA 只留 PCMA，没有才退 PCMU
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


    /**
     * 触发 ZLM 把当前活跃流录成 MP4。对应 ZLM /index/api/startRecord。
     * 语义是"开启录制能力"，不是"开始录"：触发后 ZLM 在流存在期间按 period=日期 切片写 MP4，
     * 流消失时当前文件落盘并触发 on_record_mp4。下载场景在 DOWNLOAD INVITE 流就绪后调一次，
     * 设备发完时间段流 BYE 后 ZLM 自己收尾。
     *
     * @param server    节点
     * @param app       应用名（国标配 rtp）
     * @param stream    流名（deviceId_channelId_起_止）
     * @param period    按日期切片，格式 yyyy-MM-dd
     * @param maxSecond 单文件最大时长（秒），0=不限
     * @return true=ZLM 接受（code=0）；false=ZLM 拒绝或异常
     */
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

    /**
     * 查某路流当前真实的录制态，给录像对账补偿用（外部 stopRecord 或 ZLM 丢了录制态时平台收不到事件）。
     * 对应 ZLM /index/api/isRecording：
     *
     * RECORDING 是 ZLM 明确返回 status=true；IDLE 是流在但没在录，可以放心重发 startRecord；
     * UNAVAILABLE 是调用失败或流不存在，调用方得自己用 isMediaOnline 区分流没了还是接口抖动，别盲目重发。
     */
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

    /** ZLM 录制态查询结果，见 {@link #isRecording} */
    public enum RecordState {
        RECORDING, IDLE, UNAVAILABLE
    }

    /**
     * 列出某段时间对应的 MP4 录制文件。对应 ZLM /index/api/getMp4Record。
     * on_record_mp4 回调里读 path 拿到的是单段文件，这个方法按 (应用, 流, 周期) 拉全量列表，
     * hook 漏报时也能补拉。
     *
     * @return 文件路径列表（可能为空）；ZLM 拒绝时返回空列表，不抛异常，调用方按"没找到"处理
     */
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

    /**
     * 推送 ZLM 全局配置（hook URL、api.secret、rtp_proxy.port_range 等）。
     * 对应 ZLM /index/api/setServerConfig，一般等默认节点 ready 之后调一次，
     * 把 solo 的 hook URL 写进 ZLM 全局配置，之后推流、RTP 收流这些事件都会回调过来。
     *
     * @param params 键值对，key 都带 ZLM 配置前缀（如 api.secret、hook.on_stream_changed）
     *               内部会把 {@code secret} 合并进 body，不需要调用方再传。
     * @return true=ZLM 接受（code=0），false=ZLM 拒绝或网络异常
     */
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

    /**
     * 把 on_record_mp4 回调里的相对下载地址补成绝对 URL。
     * ZLM 给的 url 形如 record/rtp/xx/2026-10-23/13-30-50-0.mp4，相对 HTTP 根、不带前导斜杠，
     * 直接丢给 HttpClient 会报 "URI with undefined scheme"。已经是 http(s) 绝对地址的原样返回。
     */
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
            // 底层异常（连接拒绝/超时/JSON 异常等）很多 getMessage() 为 null，
            // 回退到类名兜底；不打完整堆栈（健康探测每 10s 一次，堆栈刷屏），
            // 堆栈仍保留在抛出的 ServiceException cause 里，需要时可从全局异常链捞
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
