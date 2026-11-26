package com.solo.gb28181.transmit.cmd;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

// RTSP body 每行都得是 \r\n，最后还要空一行；Scale 用 Locale.US，中文系统的小数逗号设备不认。
public final class RtspControlBuilder {

    private RtspControlBuilder() {
    }

    public static String pause() {
        return "PAUSE RTSP/1.0\r\n"
                + "CSeq: " + nextCseq() + "\r\n"
                + "PauseTime: now\r\n\r\n";
    }

    public static String resume() {
        return "PLAY RTSP/1.0\r\n"
                + "CSeq: " + nextCseq() + "\r\n"
                + "Range: npt=now-\r\n\r\n";
    }

    public static String seek(long seekSec) {
        return "PLAY RTSP/1.0\r\n"
                + "CSeq: " + nextCseq() + "\r\n"
                + "Range: npt=" + Math.max(0L, seekSec) + "-\r\n\r\n";
    }

    // Range: npt=now- 不能省，海康/大华/宇视可能忽略 Scale，或者从头按新倍速播。
    public static String scale(double speed) {
        return "PLAY RTSP/1.0\r\n"
                + "CSeq: " + nextCseq() + "\r\n"
                + "Range: npt=now-\r\n"
                + "Scale: " + String.format(Locale.US, "%.6f", speed) + "\r\n\r\n";
    }

    // 这是 RTSP body 的 CSeq，跟 SIP INFO 头里的 CSeq 没关系。
    private static int nextCseq() {
        return ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999);
    }
}
