package com.solo.gb28181.transmit.cmd;

public final class SdpBuilder {

    public static final String TRANSPORT_UDP = "UDP";
    public static final String TRANSPORT_TCP_PASSIVE = "TCP-passive";
    public static final String TRANSPORT_TCP_ACTIVE = "TCP-active";

    public static final int PT_PS = 96;
    public static final int PT_PCMA = 8;

    private SdpBuilder() {
    }

    public static String normalizeTransport(String t) {
        if (t == null || t.isBlank()) return TRANSPORT_UDP;
        if (TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(t)) return TRANSPORT_TCP_PASSIVE;
        if (TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(t)) return TRANSPORT_TCP_ACTIVE;
        return TRANSPORT_UDP;
    }

    public static String buildPlay(String platformId, String sdpIp, int rtpPort,
                                   String ssrc, String transport, String streamNumber) {
        return buildInternal("Play", platformId, sdpIp, rtpPort, ssrc, transport, 0L, 0L, null, streamNumber);
    }

    public static String buildPlayback(String platformId, String sdpIp, int rtpPort, String ssrc,
                                       String transport, long startEpochSec, long endEpochSec,
                                       String streamNumber) {
        return buildInternal("Playback", platformId, sdpIp, rtpPort, ssrc, transport,
                startEpochSec, endEpochSec, null, streamNumber);
    }

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
        sb.append("c=IN IP4 ").append(sdpIp).append("\r\n");
        // t= 行直接写 Unix epoch 秒数（主流 SIP 平台都这么发）。RFC 4566 §5.9 / GB/T 28181-2016
        // 附录 G 规定用 NTP（1900 起点），实际设备不严格校验，几乎全兼容 Unix。
        // 0 0 是实时播放哨兵值，原样透传。
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

    public static String extractSsrc(String sdpBody) {
        if (sdpBody == null) return null;
        for (String line : sdpBody.split("\\r?\\n")) {
            if (line.startsWith("y=")) {
                return line.substring(2).trim();
            }
        }
        return null;
    }

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

    public static String extractSessionName(String sdpBody) {
        if (sdpBody == null) return null;
        for (String line : sdpBody.split("\\r?\\n")) {
            if (line.startsWith("s=")) {
                return line.substring(2).trim();
            }
        }
        return null;
    }

    // 这里站在上级视角：active 是上级来连本平台，passive 是上级等本平台去连。
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

    // 回给上级的 sendonly SDP；TCP 的 setup 要和上级反过来。
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

    public static String buildBroadcastAnswer(String platformId, String sdpIp, int sendPort,
                                              String ssrc, String upstreamTransport, int pt) {
        return buildAudioAnswer(platformId, sdpIp, sendPort, ssrc, upstreamTransport, pt, "sendonly");
    }

    public static String buildTalkAnswer(String platformId, String sdpIp, int recvPort,
                                         String ssrc, String upstreamTransport, int pt,
                                         String answerDirection) {
        return buildAudioAnswer(platformId, sdpIp, recvPort, ssrc, upstreamTransport, pt, answerDirection);
    }

    // 只看 m=audio 下面的方向行；没写就按 RFC 3264 默认 sendrecv。
    public static String extractAudioDirection(String sdpBody) {
        if (sdpBody == null) {
            return "sendrecv";
        }
        boolean inAudio = false;
        for (String line : sdpBody.split("\\r?\\n")) {
            String l = line.trim();
            if (l.startsWith("m=")) {
                inAudio = l.startsWith("m=audio");
            } else if (inAudio && (l.equals("a=sendrecv") || l.equals("a=sendonly")
                    || l.equals("a=recvonly") || l.equals("a=inactive"))) {
                return l.substring(2);
            }
        }
        return "sendrecv";
    }

    private static String buildAudioAnswer(String platformId, String sdpIp, int port,
                                           String ssrc, String upstreamTransport, int pt,
                                           String direction) {
        boolean tcp = TRANSPORT_TCP_PASSIVE.equalsIgnoreCase(upstreamTransport)
                || TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(upstreamTransport);
        boolean upstreamActive = TRANSPORT_TCP_ACTIVE.equalsIgnoreCase(upstreamTransport);
        StringBuilder sb = new StringBuilder(300);
        sb.append("v=0\r\n");
        sb.append("o=").append(platformId).append(" 0 0 IN IP4 ").append(sdpIp).append("\r\n");
        sb.append("s=Play\r\n");
        sb.append("c=IN IP4 ").append(sdpIp).append("\r\n");
        sb.append("t=0 0\r\n");
        sb.append("m=audio ").append(port).append(' ')
                .append(tcp ? "TCP/RTP/AVP" : "RTP/AVP")
                .append(' ').append(pt).append("\r\n");
        sb.append("a=").append(direction).append("\r\n");
        if (pt == PT_PS) {
            sb.append("a=rtpmap:96 PS/90000\r\n");
        } else {
            sb.append("a=rtpmap:8 PCMA/8000\r\n");
        }
        if (tcp) {
            sb.append("a=setup:").append(upstreamActive ? "passive" : "active").append("\r\n");
            sb.append("a=connection:new\r\n");
        }
        sb.append("y=").append(ssrc).append("\r\n");
        return sb.toString();
    }

    public static String buildAudioRecvonlyOffer(String platformId, String sdpIp, int recvPort,
                                                 String ssrc) {
        StringBuilder sb = new StringBuilder(300);
        sb.append("v=0\r\n");
        sb.append("o=").append(platformId).append(" 0 0 IN IP4 ").append(sdpIp).append("\r\n");
        sb.append("s=Play\r\n");
        sb.append("c=IN IP4 ").append(sdpIp).append("\r\n");
        sb.append("t=0 0\r\n");
        sb.append("m=audio ").append(recvPort).append(" RTP/AVP 96 8 0\r\n");
        sb.append("a=recvonly\r\n");
        sb.append("a=rtpmap:96 PS/90000\r\n");
        sb.append("a=rtpmap:8 PCMA/8000\r\n");
        sb.append("a=rtpmap:0 PCMU/8000\r\n");
        sb.append("y=").append(ssrc).append("\r\n");
        return sb.toString();
    }

    // 优先裸 PCMA；大华等设备同时列 8/96 时也别误选 PS。
    public static int selectAudioPayload(String deviceSdp) {
        boolean supportsPcma = false;
        boolean supportsPs = false;
        for (String line : deviceSdp == null ? new String[0] : deviceSdp.split("\\r?\\n")) {
            String l = line.trim();
            if (l.startsWith("m=audio")) {
                String[] parts = l.split("\\s+");
                for (int i = 3; i < parts.length; i++) {
                    if ("8".equals(parts[i])) supportsPcma = true;
                    if ("96".equals(parts[i])) supportsPs = true;
                }
            }
        }
        if (supportsPcma) return PT_PCMA;
        if (supportsPs) return PT_PS;
        return PT_PCMA;
    }

    public static int extractAudioPort(String sdpBody) {
        if (sdpBody == null) return -1;
        for (String line : sdpBody.split("\\r?\\n")) {
            if (line.startsWith("m=audio")) {
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

    public static String extractAudioTransport(String sdpBody) {
        if (sdpBody == null) return TRANSPORT_UDP;
        boolean tcp = false;
        String setup = null;
        for (String line : sdpBody.split("\\r?\\n")) {
            String l = line.trim();
            if (l.startsWith("m=audio") && l.toUpperCase().contains("TCP/RTP/AVP")) {
                tcp = true;
            } else if (l.toLowerCase().startsWith("a=setup:")) {
                setup = l.substring("a=setup:".length()).trim().toLowerCase();
            }
        }
        if (!tcp) return TRANSPORT_UDP;
        return "active".equals(setup) ? TRANSPORT_TCP_ACTIVE : TRANSPORT_TCP_PASSIVE;
    }
}
