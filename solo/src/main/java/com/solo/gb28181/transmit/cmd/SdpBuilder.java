package com.solo.gb28181.transmit.cmd;

/**
 * GB28181 INVITE SDP 报文体构造
 * <p>实时点播（{@code s=Play}）/ 历史回放（{@code s=Playback}）/ 录像下载（{@code s=Download}）共用
 * {@link #buildInternal} 内部构造，仅会话名 / t= / downloadspeed 不同。</p>
 *
 * <h3>SDP 结构（参考 GB/T 28181-2016 附录 G）</h3>
 * <pre>
 * v=0
 * o=&lt;platformId&gt; 0 0 IN IP4 &lt;sdpIp&gt;
 * s=Play / Playback / Download
 * c=IN IP4 &lt;sdpIp&gt;
 * t=&lt;startEpoch&gt; &lt;endEpoch&gt;            (Unix 秒数；Play 时 0 0)
 * m=video &lt;rtpPort&gt; {RTP/AVP|TCP/RTP/AVP} 96 97 98 99
 * a=recvonly
 * a=rtpmap:96 PS/90000
 * a=rtpmap:97 MPEG4/90000
 * a=rtpmap:98 H264/90000
 * a=rtpmap:99 H265/90000
 * [a=setup:passive\na=connection:new]    -- TCP-passive 时
 * [a=setup:active\na=connection:new]     -- TCP-active 时
 * [a=&lt;streamNumber&gt;]                     -- 子码流时（如 a=streamnumber:1 / a=stream:1 / a=streamMode:sub）
 * [a=downloadspeed:N]                    -- Download 时（如 a=downloadspeed:4）
 * y=&lt;ssrc&gt;
 * </pre>
 *
 * <h3>为什么 m=video 多了 97 MPEG4；不写 a=fmtp:98 profile-level-id 也不写 u= 行</h3>
 * <p>多厂商 / 老固件 SDP 兼容性补丁：</p>
 * <ul>
 *   <li>部分老固件不识别 m=video 只列 96/98/99 三档 → 加 97 MPEG4 给一个备选（不支持的设备按 SDP 规范忽略）
 *   <li><b>不写</b> {@code a=fmtp:98 profile-level-id}：参考 wvp-pro 默认实现。
 *       1927622 commit 加的"profile 必带减少重协商"实践在实测中不成立——某厂商级联设备（50030000002000000120）
 *       认为平台声明的 profile 与设备实际编码不一致时回 400 Bad Request。去掉这行，跨厂商更稳。
 *   <li><b>不写</b> {@code u=} 会话 URI：1927622 commit 加的"防止 488"实践在实测中也不成立——
 *       同一设备对 {@code u=<platformId>:0} 的 ufrag 不接受，回 400。wvp-pro 默认不带，跨厂商更稳。
 * </ul>
 */
public final class SdpBuilder {

    /** 收流模式 — UDP */
    public static final String TRANSPORT_UDP = "UDP";
    /** 收流模式 — TCP 被动（设备主动连 ZLM） */
    public static final String TRANSPORT_TCP_PASSIVE = "TCP-passive";
    /** 收流模式 — TCP 主动（ZLM 主动连设备） */
    public static final String TRANSPORT_TCP_ACTIVE = "TCP-active";

    private SdpBuilder() {
    }

    /**
     * 把上层传入的传输方式字符串归一成 {@link #TRANSPORT_UDP} / {@link #TRANSPORT_TCP_PASSIVE}
     * / {@link #TRANSPORT_TCP_ACTIVE} 之一；空值/未知值回落到 UDP。
     */
    public static String normalizeTransport(String t) {
        if (t == null || t.isBlank()) return TRANSPORT_UDP;
        if (TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(t)) return TRANSPORT_TCP_PASSIVE;
        if (TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(t)) return TRANSPORT_TCP_ACTIVE;
        return TRANSPORT_UDP;
    }

    /**
     * 构造实时点播 SDP body（{@code s=Play}, {@code t=0 0}）
     *
     * @param platformId 本平台 SIP ID（用于 o= 行 username）
     * @param sdpIp      本平台对设备暴露的 IP（c= 行 + o= 行）
     * @param rtpPort    ZLM 分配的收流 RTP 端口（m= 行）
     * @param ssrc       10 位 SSRC（y= 行）
     * @param transport  {@link #TRANSPORT_UDP} / {@link #TRANSPORT_TCP_PASSIVE} / {@link #TRANSPORT_TCP_ACTIVE}
     */
    public static String buildPlay(String platformId, String sdpIp, int rtpPort,
                                   String ssrc, String transport, String streamNumber) {
        return buildInternal("Play", platformId, sdpIp, rtpPort, ssrc, transport, 0L, 0L, null, streamNumber);
    }

    /**
     * 构造历史回放 SDP body（{@code s=Playback}, {@code t=<startEpoch> <endEpoch>}）
     *
     * @param startEpochSec 回放起始 Unix 秒
     * @param endEpochSec   回放结束 Unix 秒
     * @param streamNumber  码流 token（主码流传 null/空，子码流原样写进 {@code a=<token>}；与点播共用一套写法）
     */
    public static String buildPlayback(String platformId, String sdpIp, int rtpPort, String ssrc,
                                       String transport, long startEpochSec, long endEpochSec,
                                       String streamNumber) {
        return buildInternal("Playback", platformId, sdpIp, rtpPort, ssrc, transport,
                startEpochSec, endEpochSec, null, streamNumber);
    }

    /**
     * 构造录像下载 SDP body（{@code s=Download}, {@code t=<startEpoch> <endEpoch>}, {@code a=downloadspeed:N}）
     * <p>S3 暂不调用，预留给 S3.2 / S4 下载阶段。</p>
     *
     * @param startEpochSec 起始 Unix 秒
     * @param endEpochSec   结束 Unix 秒
     * @param downloadSpeed 下载倍速（1/2/4/8）
     */
    public static String buildDownload(String platformId, String sdpIp, int rtpPort, String ssrc,
                                       String transport, long startEpochSec, long endEpochSec,
                                       int downloadSpeed) {
        return buildInternal("Download", platformId, sdpIp, rtpPort, ssrc, transport,
                startEpochSec, endEpochSec, downloadSpeed, null);
    }

    private static String buildInternal(String sessionName, String platformId, String sdpIp, int rtpPort,
                                        String ssrc, String transport, long startEpoch, long endEpoch,
                                        Integer downloadSpeed, String streamNumber) {
        boolean tcp = TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(transport)
                || TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(transport);
        boolean tcpActive = TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(transport);
        StringBuilder sb = new StringBuilder(380);
        sb.append("v=0\r\n");
        sb.append("o=").append(platformId).append(" 0 0 IN IP4 ").append(sdpIp).append("\r\n");
        sb.append("s=").append(sessionName).append("\r\n");
        // u= 行：1927622 commit 加上（备注"少了部分老固件会回 488"）。
        // 实测某厂商级联设备（50030000002000000120）认为本平台声明的 u= ufrag 不合法 → 400 Bad Request。
        // wvp-pro 默认不带 u=，跨厂商更稳。暂对齐 wvp-pro，回滚到 1927622 之前的形态。
        // sb.append("u=").append(platformId).append(":0\r\n");
        sb.append("c=IN IP4 ").append(sdpIp).append("\r\n");
        // t= 行直接写 Unix epoch 秒数（主流实现 wvp-pro / 海康 / 大华 / 宇视 都这么发）。
        // RFC 4566 §5.9 / GB/T 28181-2016 附录 G 规定用 NTP（1900 起点），实际设备不严格校验，
        // 几乎全兼容 Unix。0 0 是实时播放哨兵值，原样透传。
        sb.append("t=").append(startEpoch).append(' ').append(endEpoch).append("\r\n");
        // m=video PT 列表：96 PS / 97 MPEG4 / 98 H264 / 99 H265。多了 97 让老固件多一个备选，
        // 不支持的设备按 SDP 规范忽略、不影响主流程。
        sb.append("m=video ").append(rtpPort).append(' ')
                .append(tcp ? "TCP/RTP/AVP" : "RTP/AVP")
                .append(" 96 97 98 99\r\n");
        sb.append("a=recvonly\r\n");
        sb.append("a=rtpmap:96 PS/90000\r\n");
        sb.append("a=rtpmap:97 MPEG4/90000\r\n");
        sb.append("a=rtpmap:98 H264/90000\r\n");
        // H264 profile-level-id：1927622 commit 加上（参考海康/大华"profile 必带减少重协商"实践）。
        // 但实测某厂商级联设备（设备 ID 50030000002000000120）认为本平台声明的 profile 与设备实际编码
        // profile 不一致时回 400 Bad Request；wvp-pro 默认不写这行，跨厂商更稳。暂对齐 wvp-pro。
        // sb.append("a=fmtp:98 profile-level-id=42e01e\r\n");
        sb.append("a=rtpmap:99 H265/90000\r\n");
        if (tcp) {
            sb.append("a=setup:").append(tcpActive ? "active" : "passive").append("\r\n");
            sb.append("a=connection:new\r\n");
        }
        // 码流标识（GB/T 28181-2022 streamnumber 及各厂商写法），原样写入 a= 行（允许带冒号，如
        // a=streamnumber:1 / a=stream:1 / a=streamMode:sub）。空表示主码流，不写此行、报文与旧版一致。
        if (streamNumber != null && !streamNumber.isBlank()) {
            sb.append("a=").append(streamNumber.trim()).append("\r\n");
        }
        if (downloadSpeed != null && downloadSpeed > 0) {
            sb.append("a=downloadspeed:").append(downloadSpeed).append("\r\n");
        }
        sb.append("y=").append(ssrc).append("\r\n");
        return sb.toString();
    }

    /**
     * 从 200 OK SDP body 中抓 {@code y=<ssrc>}；不存在返回 null。
     */
    public static String extractSsrc(String sdpBody) {
        if (sdpBody == null) return null;
        for (String line : sdpBody.split("\\r?\\n")) {
            if (line.startsWith("y=")) {
                return line.substring(2).trim();
            }
        }
        return null;
    }

    /**
     * 从 200 OK SDP body 中抓 {@code o=<user> ...} 的 username；不存在返回 null。
     */
    public static String extractOriginUser(String sdpBody) {
        if (sdpBody == null) return null;
        for (String line : sdpBody.split("\\r?\\n")) {
            if (line.startsWith("o=")) {
                String rest = line.substring(2).trim();
                int sp = rest.indexOf(' ');
                return sp > 0 ? rest.substring(0, sp) : rest;
            }
        }
        return null;
    }

    /**
     * 从 SDP body 抓连接地址 {@code c=IN IP4 <ip>} 的 IP；不存在返回 null。
     * <p>上级 INVITE 里 c= 行即上级期望收流的 IP。</p>
     */
    public static String extractConnectionIp(String sdpBody) {
        if (sdpBody == null) return null;
        for (String line : sdpBody.split("\\r?\\n")) {
            if (line.startsWith("c=")) {
                // c=IN IP4 1.2.3.4
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3) {
                    return parts[2];
                }
            }
        }
        return null;
    }

    /**
     * 从 SDP body 抓 {@code m=video <port> ...} 的端口；不存在/非法返回 -1。
     * <p>上级 INVITE 里 m= 行端口即上级期望收流的端口（UDP/TCP 主动去连的目标端口）。</p>
     */
    public static int extractMediaPort(String sdpBody) {
        if (sdpBody == null) return -1;
        for (String line : sdpBody.split("\\r?\\n")) {
            if (line.startsWith("m=video")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        return Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignore) {
                        return -1;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 从 SDP body 抓会话名 {@code s=<name>}（Play / Playback / Download）；不存在返回 null。
     */
    public static String extractSessionName(String sdpBody) {
        if (sdpBody == null) return null;
        for (String line : sdpBody.split("\\r?\\n")) {
            if (line.startsWith("s=")) {
                return line.substring(2).trim();
            }
        }
        return null;
    }

    /**
     * 判断上级 SDP 的传输语义，返回归一后的 transport 常量。
     * <p>{@code m=video <port> TCP/RTP/AVP ...} + {@code a=setup:active} → 上级主动来连本平台（本平台 TCP 被动收听）；
     * {@code a=setup:passive} → 上级被动等本平台去连；无 TCP 协议 → UDP。</p>
     *
     * @return {@link #TRANSPORT_UDP} / {@link #TRANSPORT_TCP_PASSIVE} / {@link #TRANSPORT_TCP_ACTIVE}
     *         语义站在<strong>上级</strong>视角：TCP_ACTIVE=上级主动来连；TCP_PASSIVE=上级等本平台去连
     */
    public static String extractTransport(String sdpBody) {
        if (sdpBody == null) return TRANSPORT_UDP;
        boolean tcp = false;
        String setup = null;
        for (String line : sdpBody.split("\\r?\\n")) {
            String l = line.trim();
            if (l.startsWith("m=video") && l.toUpperCase().contains("TCP/RTP/AVP")) {
                tcp = true;
            } else if (l.toLowerCase().startsWith("a=setup:")) {
                setup = l.substring("a=setup:".length()).trim().toLowerCase();
            }
        }
        if (!tcp) return TRANSPORT_UDP;
        return "active".equals(setup) ? TRANSPORT_TCP_ACTIVE : TRANSPORT_TCP_PASSIVE;
    }

    /**
     * 构造本平台作为<strong>发流方</strong>回给上级的 200 OK SDP（{@code a=sendonly}）。
     * <p>用于级联被上级点播：本平台向真实设备取流后，用此 SDP 告诉上级最终的媒体地址/端口/SSRC。</p>
     *
     * <pre>
     * v=0
     * o=&lt;platformId&gt; 0 0 IN IP4 &lt;sdpIp&gt;
     * s=Play
     * c=IN IP4 &lt;sdpIp&gt;
     * t=0 0
     * m=video &lt;sendPort&gt; {RTP/AVP|TCP/RTP/AVP} 96
     * a=sendonly
     * a=rtpmap:96 PS/90000
     * [a=setup:active|passive]      -- TCP 时，与上级取反
     * y=&lt;ssrc&gt;
     * </pre>
     *
     * @param platformId    本平台对该上级使用的国标编号（deviceGbId），写 o= 行 username
     * @param sdpIp         本平台发流的媒体 IP（c=/o= 行）
     * @param sendPort      本平台发流端口（m= 行；UDP/TCP 主动去连时可为 0，TCP 被动监听时为 ZLM 监听端口）
     * @param ssrc          最终 SSRC（y= 行，回给上级）
     * @param upstreamTransport 上级 SDP 解析出的传输语义（{@link #extractTransport} 的结果）
     */
    public static String buildSendAnswer(String platformId, String sdpIp, int sendPort,
                                         String ssrc, String upstreamTransport) {
        boolean tcp = TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(upstreamTransport)
                || TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(upstreamTransport);
        // setup 与上级取反：上级 active(来连)→本平台 passive(被动)；上级 passive→本平台 active(主动去连)
        boolean upstreamActive = TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(upstreamTransport);
        StringBuilder sb = new StringBuilder(256);
        sb.append("v=0\r\n");
        sb.append("o=").append(platformId).append(" 0 0 IN IP4 ").append(sdpIp).append("\r\n");
        sb.append("s=Play\r\n");
        sb.append("c=IN IP4 ").append(sdpIp).append("\r\n");
        sb.append("t=0 0\r\n");
        sb.append("m=video ").append(sendPort).append(' ')
                .append(tcp ? "TCP/RTP/AVP" : "RTP/AVP")
                .append(" 96\r\n");
        sb.append("a=sendonly\r\n");
        sb.append("a=rtpmap:96 PS/90000\r\n");
        if (tcp) {
            sb.append("a=setup:").append(upstreamActive ? "passive" : "active").append("\r\n");
            sb.append("a=connection:new\r\n");
        }
        sb.append("y=").append(ssrc).append("\r\n");
        return sb.toString();
    }
}
