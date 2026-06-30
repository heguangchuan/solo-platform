package com.solo.gb28181.transmit.cmd;

/**
 * GB28181 INVITE SDP 报文体构造
 * <p>S2.2 范围：仅生成实时点播（{@code s=Play}）所需的最小 SDP；
 * 回放/下载（{@code s=Playback/Download}）后续 Sprint 接入时再扩。</p>
 *
 * <h3>SDP 结构（参考 GB/T 28181-2016 附录 G、wvp-pro {@code SIPCommander.playStreamCmd}）</h3>
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
 * y=&lt;ssrc&gt;
 * </pre>
 */
public final class SdpBuilder {

    /** 收流模式 — UDP */
    public static final String TRANSPORT_UDP = "UDP";
    /** 收流模式 — TCP 被动（设备主动连 ZLM） */
    public static final String TRANSPORT_TCP_PASSIVE = "TCP-passive";

    private SdpBuilder() {
    }

    /**
     * 构造实时点播 SDP body（{@code s=Play}, {@code t=0 0}）
     *
     * @param platformId 本平台 SIP ID（用于 o= 行 username）
     * @param sdpIp      本平台对设备暴露的 IP（c= 行 + o= 行）
     * @param rtpPort    ZLM 分配的收流 RTP 端口（m= 行）
     * @param ssrc       10 位 SSRC（y= 行）
     * @param transport  {@link #TRANSPORT_UDP} 或 {@link #TRANSPORT_TCP_PASSIVE}
     */
    public static String buildPlay(String platformId, String sdpIp, int rtpPort,
                                   String ssrc, String transport) {
        return buildInternal("Play", platformId, sdpIp, rtpPort, ssrc, transport, 0L, 0L, null);
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
                startEpochSec, endEpochSec, null);
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
                startEpochSec, endEpochSec, downloadSpeed);
    }

    private static String buildInternal(String sessionName, String platformId, String sdpIp, int rtpPort,
                                        String ssrc, String transport, long startEpoch, long endEpoch,
                                        Integer downloadSpeed) {
        boolean tcp = TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(transport);
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
            sb.append("a=setup:passive\r\n");
            sb.append("a=connection:new\r\n");
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
}
