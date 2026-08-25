package com.solo.media.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.common.ServiceException;
import com.solo.media.entity.MediaServer;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ZLMediaKit REST API 客户端
 * <p>ZLM 接口约定（form-data，secret 走 query 或 body 均可，这里统一拼到 body）：
 * {@code POST http://<host>:<httpPort>/index/api/<method>?secret=xxx&k=v...}</p>
 * <p>所有调用都走 form-urlencoded body；响应统一 JSON：{@code {"code":0,...}}，
 * code != 0 抛 {@link ServiceException}。</p>
 *
 * <h3>覆盖的接口（S2.1/S2.2 + ONVIF）</h3>
 * <ul>
 *     <li>{@code openRtpServer}        — 申请 RTP 收流端口（国标 INVITE）</li>
 *     <li>{@code closeRtpServer}       — 关闭 RTP 收流端口</li>
 *     <li>{@code getMediaList}         — 查询流是否还活着</li>
 *     <li>{@code getRtpInfo}           — 查询 RTP 端口状态（保活）</li>
 *     <li>{@code addStreamProxy}       — 让 ZLM 从 RTSP/RTMP 源拉流（ONVIF 点播）</li>
 *     <li>{@code delStreamProxy}       — 移除拉流代理</li>
 *     <li>{@code startSendRtp}         — 把 ZLM 里一路流按 PS/RTP 推到指定 ip:port（级联上级点播）</li>
 *     <li>{@code startSendRtpPassive}  — 开监听端口等对端来连后推流（TCP 主动模式，上级来连本平台）</li>
 *     <li>{@code stopSendRtp}          — 停止推流</li>
 * </ul>
 * <p>注：截图由 jessibuca 播放器自带，不在本类。</p>
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
     * <p>本平台固定<b>多端口</b>收流，每路流独占一个 RTP 端口，靠端口即可唯一定位流，
     * 因此<b>不下发 {@code ssrc} 参数</b>——ZLM 收下该端口上的任意 SSRC。
     * 下级级联平台普遍自报 SSRC、无视上级 INVITE 里给的值，按 SSRC 过滤会直接把包丢掉。
     * 详见 ADR-025。</p>
     *
     * @param server   节点
     * @param streamId ZLM stream 名（约定 app=rtp、stream=&lt;deviceId&gt;_&lt;channelId&gt;，传完整 stream 部分即可）
     * @param tcpMode  0=UDP，1=TCP-passive，2=TCP-active
     * @param port     0=自动分配（推荐），&gt;0=指定端口
     * @return ZLM 实际分配的端口
     */
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
        log.info("[流媒体] openRtpServer 结果:成功 节点:{} 流ID:{} 端口:{}（不按 SSRC 过滤）",
                server.getServerId(), streamId, actualPort);
        return actualPort;
    }

    /**
     * 关闭 RTP 收流端口
     *
     * @return true=确实关闭了；false=已经不存在
     */
    public boolean closeRtpServer(MediaServer server, String streamId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        try {
            JsonNode resp = post(server, "closeRtpServer", params);
            boolean hit = resp.path("hit").asInt(0) == 1;
            log.info("[流媒体] closeRtpServer 节点:{} 流ID:{} 命中:{}",
                    server.getServerId(), streamId, hit);
            return hit;
        } catch (ServiceException e) {
            log.warn("[流媒体] closeRtpServer 失败(忽略) 节点:{} 流ID:{} 错误:{}",
                    server.getServerId(), streamId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 流是否存在
     */
    public boolean isMediaOnline(MediaServer server, String app, String stream) {
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
     * 获取节点上的所有流列表（推流管理"兜底同步"用）
     * <p>与 {@link #isMediaOnline} 复用同一底层，只是不加过滤参数，全量拉。</p>
     *
     * @return 每条包含 app/stream/schema/originType/originTypeStr/originUrl/totalReaderCount/aliveSecond 等
     */
    public List<JsonNode> getMediaList(MediaServer server) {
        try {
            JsonNode resp = post(server, "getMediaList", new LinkedHashMap<>());
            JsonNode data = resp.path("data");
            if (!data.isArray()) return List.of();
            List<JsonNode> out = new ArrayList<>();
            data.forEach(out::add);
            return out;
        } catch (ServiceException e) {
            log.warn("[流媒体] getMediaList 失败 节点:{} 错误:{}", server.getServerId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 关闭指定 app/stream 的流（强制中止推流用）
     *
     * @param force true=不管有没有观众都关
     * @return true=有一路流被关；false=不存在或失败
     */
    public boolean closeStream(MediaServer server, String app, String stream, boolean force) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app", app);
        params.put("stream", stream);
        params.put("force", force ? "1" : "0");
        try {
            JsonNode resp = post(server, "close_streams", params);
            int count = resp.path("count_hit").asInt(0);
            log.info("[流媒体] close_streams 结果:成功 节点:{} 应用:{} 流ID:{} 命中数:{}",
                    server.getServerId(), app, stream, count);
            return count > 0;
        } catch (ServiceException e) {
            log.warn("[流媒体] close_streams 失败 节点:{} 应用:{} 流ID:{} 错误:{}",
                    server.getServerId(), app, stream, e.getMessage(), e);
            return false;
        }
    }

    /**
     * RTP 端口保活查询
     */
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

    /**
     * 暂停 ZLM 端 RTP 收流（不向设备发任何 SIP 报文，纯 ZLM 侧停收）。
     * <p>用于历史回放 pause 场景：MANSRTSP PAUSE 设备不响应时，靠这个让 ZLM 立刻停收，
     * 不依赖设备固件行为。流注册（{@code on_publish} / {@code on_stream_changed}）不会被触发，
     * 后续 {@link #resumeRtpServer} 可恢复接收同一路 RTP，不需要重新 INVITE。</p>
     *
     * @return true=ZLM 接受（code=0）；false=调用失败（异常已捕获，记 WARN，不抛）
     */
    public boolean pauseRtpServer(MediaServer server, String streamId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        try {
            JsonNode resp = post(server, "pauseRtpServer", params);
            log.info("[流媒体] pauseRtpServer 结果:成功 节点:{} 流ID:{} 命中:{}",
                    server.getServerId(), streamId, resp.path("hit").asInt(0));
            return true;
        } catch (Exception e) {
            log.warn("[流媒体] pauseRtpServer 失败 节点:{} 流ID:{} 错误:{}",
                    server.getServerId(), streamId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 恢复 ZLM 端 RTP 收流（与 {@link #pauseRtpServer} 配对）。
     *
     * @return true=ZLM 接受（code=0）；false=调用失败
     */
    public boolean resumeRtpServer(MediaServer server, String streamId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("stream_id", streamId);
        try {
            JsonNode resp = post(server, "resumeRtpServer", params);
            log.info("[流媒体] resumeRtpServer 结果:成功 节点:{} 流ID:{} 命中:{}",
                    server.getServerId(), streamId, resp.path("hit").asInt(0));
            return true;
        } catch (Exception e) {
            log.warn("[流媒体] resumeRtpServer 失败 节点:{} 流ID:{} 错误:{}",
                    server.getServerId(), streamId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 让 ZLM 从一个 RTSP/RTMP 源拉流，产出 app/stream 名下的可分发流。
     * <p>ONVIF 点播场景：源是 {@code rtsp://user:pwd@host:port/path}，ZLM 拉起后暴露 HTTP-FLV/HLS/WS-FLV/WebRTC。</p>
     *
     * @param server   节点
     * @param app      app 名（ONVIF 约定 {@code rtsp}，与国标 GB 用的 {@code rtp} 区分）
     * @param stream   stream 名（ONVIF 约定 {@code onvif_<deviceId>}）
     * @param url      源 URL（含账密）
     * @param rtpType  RTSP 传输：0=TCP+UDP 自适应，1=TCP，2=UDP，3=组播
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
        log.info("[流媒体] addStreamProxy 结果:成功 节点:{} 应用:{} 流ID:{} 传输:{} key:{}",
                server.getServerId(), app, stream, rtpType, key);
        return key;
    }

    /**
     * 移除拉流代理
     *
     * @return true=命中并关闭；false=已经不存在
     */
    public boolean delStreamProxy(MediaServer server, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", key);
        try {
            JsonNode resp = post(server, "delStreamProxy", params);
            boolean flag = resp.path("data").path("flag").asBoolean(false);
            log.info("[流媒体] delStreamProxy 节点:{} key:{} 成功:{}",
                    server.getServerId(), key, flag);
            return flag;
        } catch (ServiceException e) {
            log.warn("[流媒体] delStreamProxy 失败(忽略) 节点:{} key:{} 错误:{}",
                    server.getServerId(), key, e.getMessage(), e);
            return false;
        }
    }

    // 注：截图功能由 jessibuca 播放器自带（操作栏内置 screenshot 按钮），不走后端 ZLM API。

    // ---------------- 级联推流（sendRtp）----------------

    /**
     * 把 ZLM 里的一路流按 PS/RTP 主动推到指定目的地址（UDP 或 TCP 主动去连对端）。
     * <p>用于本平台作为下级被上级点播：先向真实设备取流拿到 {@code app=rtp/stream}，
     * 再用本方法把这路流转推给上级给出的 {@code dstIp:dstPort}。</p>
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
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        params.put("ssrc", ssrc);
        params.put("dst_url", dstIp);
        params.put("dst_port", String.valueOf(dstPort));
        params.put("is_udp", udp ? "1" : "0");   // 注意语义：UDP=1，TCP=0
        params.put("pt", "96");
        params.put("use_ps", "1");
        if (srcPort != null && srcPort > 0) {
            params.put("src_port", String.valueOf(srcPort));
        }
        JsonNode resp = post(server, "startSendRtp", params);
        int localPort = resp.path("local_port").asInt(0);
        log.info("[流媒体] startSendRtp 结果:成功 节点:{} 应用:{} 流ID:{} SSRC:{} 目的:{}:{} UDP:{} 本地端口:{}",
                server.getServerId(), app, stream, ssrc, dstIp, dstPort, udp, localPort);
        return localPort;
    }

    /**
     * 开一个监听端口等对端来连后再推流（TCP 主动模式：上级主动来连本平台的端口）。
     * <p>回给上级 200 OK 后立即调用；上级据 SDP 里本平台返回的端口来建立 TCP 连接。</p>
     *
     * @return ZLM 实际监听的本地端口（供本平台写进回复 SDP 的 {@code m=} 行）
     */
    public int startSendRtpPassive(MediaServer server, String app, String stream, String ssrc) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        params.put("ssrc", ssrc);
        params.put("pt", "96");
        params.put("use_ps", "1");
        JsonNode resp = post(server, "startSendRtpPassive", params);
        int localPort = resp.path("local_port").asInt(0);
        if (localPort <= 0) {
            throw new ServiceException("ZLM startSendRtpPassive 未返回有效端口: " + resp);
        }
        log.info("[流媒体] startSendRtpPassive 结果:成功 节点:{} 应用:{} 流ID:{} SSRC:{} 本地端口:{}",
                server.getServerId(), app, stream, ssrc, localPort);
        return localPort;
    }

    /**
     * 停止推流。
     *
     * @return true=确实停了；false=已不存在/异常（忽略）
     */
    public boolean stopSendRtp(MediaServer server, String app, String stream, String ssrc) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        if (ssrc != null && !ssrc.isBlank()) {
            params.put("ssrc", ssrc);
        }
        try {
            post(server, "stopSendRtp", params);
            log.info("[流媒体] stopSendRtp 结果:成功 节点:{} 应用:{} 流ID:{} SSRC:{}",
                    server.getServerId(), app, stream, ssrc);
            return true;
        } catch (ServiceException e) {
            log.warn("[流媒体] stopSendRtp 失败(忽略) 节点:{} 应用:{} 流ID:{} 错误:{}",
                    server.getServerId(), app, stream, e.getMessage(), e);
            return false;
        }
    }

    // ---------------- 录像（S3 录像下载） ----------------

    /**
     * 触发 ZLM 把当前活跃流录成 MP4。
     * <p>对应 ZLM {@code /index/api/startRecord} 接口。<b>语义是"开启录制能力"</b>而非"开始录"：
     * 触发后 ZLM 在流存在期间按 {@code period=日期} 切片写成 MP4 文件，流消失时本段文件落盘并触发
     * {@code on_record_mp4} hook。录像下载场景调用时机：DOWNLOAD INVITE 流就绪后立即调一次，
     * 设备发完时间段流 BYE 后 ZLM 自动完成该段 MP4。</p>
     *
     * @param server    节点
     * @param app       应用名（国标配 rtp）
     * @param stream    流名（&lt;deviceId&gt;_&lt;channelId&gt;_&lt;起&gt;_&lt;止&gt;）
     * @param period    按日期切片，格式 yyyy-MM-dd
     * @param maxSecond 单文件最大时长（秒），0=不限
     * @return true=ZLM 接受（code=0）；false=ZLM 拒绝或异常
     */
    public boolean startRecord(MediaServer server, String app, String stream, String period, int maxSecond) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("type", "mp4");
        params.put("vhost", "__defaultVhost__");
        params.put("app", app);
        params.put("stream", stream);
        params.put("period", period);
        params.put("max_second", String.valueOf(maxSecond));
        try {
            post(server, "startRecord", params);
            log.info("[流媒体] startRecord ok 节点:{} 应用:{} 流:{} 周期:{} 最长:{}s",
                    server.getServerId(), app, stream, period, maxSecond);
            return true;
        } catch (ServiceException e) {
            log.warn("[流媒体] startRecord 失败 节点:{} 应用:{} 流:{} 原因:{}",
                    server.getServerId(), app, stream, e.getMessage());
            return false;
        }
    }

    /**
     * 列出某段时间对应的 MP4 录制文件。
     * <p>对应 ZLM {@code /index/api/getMp4Record} 接口。{@code on_record_mp4} hook 回调里读
     * {@code path} 字段取到的是<b>单段</b>文件，本方法用于按 (应用, 流, 周期) 维度
     * 拉全量列表（兜底：hook 漏报时也能补拉）。</p>
     *
     * @return 文件路径列表（可能为空）；ZLM 拒绝时返回空列表（不抛异常，调用方按"未找到"处理）
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
            log.warn("[流媒体] getMp4Record 失败 节点:{} 应用:{} 流:{} 周期:{} 原因:{}",
                    server.getServerId(), app, stream, period, e.getMessage());
            return List.of();
        }
    }

    /**
     * 推送 ZLM 全局配置（hook URL、api.secret、rtp_proxy.port_range 等）
     * <p>对应 ZLM {@code /index/api/setServerConfig} 接口。调用时机由调用方决定：
     * 一般在默认节点 ready 之后调一次，把 solo 的 hook URL 写到 ZLM 全局配置里，
     * 后续 ZLM 推流 / RTP 收流等所有事件都会按这个 URL 回调 solo。</p>
     *
     * @param params 键值对，所有 key 必须带 ZLM 配置前缀（如 {@code api.secret}、{@code hook.on_stream_changed}）。
     *               内部会把 {@code secret} 合并进 body，不需要调用方再传。
     * @return true=ZLM 接受（code=0），false=ZLM 拒绝或网络异常
     */
    public boolean setServerConfig(MediaServer server, Map<String, String> params) {
        try {
            post(server, "setServerConfig", params);
            log.info("[流媒体] setServerConfig 结果:成功 节点:{} 项数:{}", server.getServerId(), params.size());
            return true;
        } catch (ServiceException e) {
            log.warn("[流媒体] setServerConfig 失败 节点:{} 错误:{}", server.getServerId(), e.getMessage(), e);
            return false;
        }
    }

    // ---------------- 底层 POST ----------------

    /**
     * 健康探测：调 ZLM {@code /index/api/version} 接口，能正常返回即视为在线
     *
     * <p>选用 {@code /version} 是因为它返回 payload 极小（仅 ZLM 版本号），
     * 不读任何运行时状态，是 ZLM 自身监控脚本的标准 ping 端点。
     * 任何异常（连接拒绝 / 超时 / 非 200 / code != 0）一律视为离线。</p>
     *
     * <p>超时受 {@link #httpClient}（5s 连接）+ {@code HttpRequest.timeout(10s)} 约束。
     * 上层 {@code CompletableFuture.orTimeout} 可提前打断，但底层线程在 connect 阶段
     * 不可中断，会一直占位到 5s 兜底——10s 探测周期足够释放，不会拖垮线程池。</p>
     */
    public boolean ping(MediaServer server) {
        try {
            post(server, "version", new LinkedHashMap<>());
            return true;
        } catch (Exception e) {
            return false;
        }
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
            JsonNode node = MAPPER.readTree(resp.body());
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
            log.warn("[流媒体] ZLM 调用异常 方法:{} URL:{} 错误:{}", method, url, detail);
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
