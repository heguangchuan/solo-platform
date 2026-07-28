package com.solo.gb28181.transmit.cmd;

/**
 * GB28181 INVITE SDP 报文体构造
 * <p>S2.2 范围：仅生成实时点播（{@code s=Play}）所需的最小 SDP；
 * 回放/下载（{@code s=Playback/Download}）后续 Sprint 接入时再扩。</p>
 *
 * <h3>SDP 结构（参考 GB/T 28181-2016 附录 G）</h3>
 * <pre>
 * v=0
 * o=&lt;platformId&gt; 0 0 IN IP4 &lt;sdpIp&gt;
 * s=Play
 * c=IN IP4 &lt;sdpIp&gt;
 * t=0 0
 * m=video &lt;rtpPort&gt; {RTP/AVP|TCP/RTP/AVP} 96 98 99
 * a=recvonly
 * a=rtpmap:96 PS/90000
 * a=rtpmap:98 H264/90000
 * a=rtpmap:99 H265/90000
 * [a=setup:passive\na=connection:new]    -- TCP-passive 时
 * [a=&lt;streamNumber&gt;]                     -- 子码流时（如 a=streamnumber:1 / a=stream:1 / a=streamMode:sub）
 * y=&lt;ssrc&gt;
 * </pre>
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
     */
    public static String buildPlayback(String platformId, String sdpIp, int rtpPort, String ssrc,
                                       String transport, long startEpochSec, long endEpochSec) {
        return buildInternal("Playback", platformId, sdpIp, rtpPort, ssrc, transport,
                startEpochSec, endEpochSec, null, null);
    }

    /**
     * 构造录像下载 SDP body（{@code s=Download}, {@code t=<startEpoch> <endEpoch>}, {@code a=downloadspeed:N}）
     * <p>S3 暂不调用，预留给 S3.2 / S4 下载阶段。</p>
     *
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
        StringBuilder sb = new StringBuilder(320);
        sb.append("v=0\r\n");
        sb.append("o=").append(platformId).append(" 0 0 IN IP4 ").append(sdpIp).append("\r\n");
        sb.append("s=").append(sessionName).append("\r\n");
        sb.append("c=IN IP4 ").append(sdpIp).append("\r\n");
        sb.append("t=").append(startEpoch).append(' ').append(endEpoch).append("\r\n");
        sb.append("m=video ").append(rtpPort).append(' ')
                .append(tcp ? "TCP/RTP/AVP" : "RTP/AVP")
                .append(" 96 98 99\r\n");
        sb.append("a=recvonly\r\n");
        sb.append("a=rtpmap:96 PS/90000\r\n");
        sb.append("a=rtpmap:98 H264/90000\r\n");
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
