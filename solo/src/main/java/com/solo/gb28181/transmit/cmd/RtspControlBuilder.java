package com.solo.gb28181.transmit.cmd;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p>SIP INFO 携带 {@code Application/MANSRTSP} body，把 RTSP 的 PAUSE/PLAY/Scale 指令带给设备，
 * 设备收到后调整推流行为（暂停/继续/跳转/变速）。</p>
 *
 * <p>所有方法返回的字符串必须严格遵守 RTSP 行结尾：以 {@code \r\n} 结束，整体以一个空行（{@code \r\n\r\n}）收尾。</p>
 *
 * <h3>四种控制语义</h3>
 * <ul>
 *   <li>{@link #pause()} — 暂停推流，画面冻结</li>
 *   <li>{@link #resume()} — 从暂停点继续</li>
 *   <li>{@link #seek(long)} — 跳到回放开始后第 N 秒</li>
 *   <li>{@link #scale(double)} — 变速（0.25/0.5/1/2/4 等）</li>
 * </ul>
 *
 * <p>Scale 浮点用 {@code Locale.US} 强制点号小数分隔符，避免 zh_CN 系统输出 {@code 1,000000}
 * 导致设备解析失败。</p>
 */
public final class RtspControlBuilder {

    private RtspControlBuilder() {
    }

    /**
     * PAUSE：暂停推流
     * <pre>
     * PAUSE RTSP/1.0
     * CSeq: 712345678
     * PauseTime: now
     * </pre>
     */
    public static String pause() {
        return "PAUSE RTSP/1.0\r\n"
                + "CSeq: " + nextCseq() + "\r\n"
                + "PauseTime: now\r\n\r\n";
    }

    /**
     * PLAY（从暂停点继续）：
     * <pre>
     * PLAY RTSP/1.0
     * CSeq: 712345679
     * Range: npt=now-
     * </pre>
     */
    public static String resume() {
        return "PLAY RTSP/1.0\r\n"
                + "CSeq: " + nextCseq() + "\r\n"
                + "Range: npt=now-\r\n\r\n";
    }

    /**
     * SEEK：跳到回放起点后第 {@code seekSec} 秒（绝对偏移）
     * <pre>
     * PLAY RTSP/1.0
     * CSeq: 712345680
     * Range: npt=60-
     * </pre>
     */
    public static String seek(long seekSec) {
        return "PLAY RTSP/1.0\r\n"
                + "CSeq: " + nextCseq() + "\r\n"
                + "Range: npt=" + Math.max(0L, seekSec) + "-\r\n\r\n";
    }

    /**
     * SCALE：变速，正常 = 1.0，慢放 0.25/0.5，快放 2/4/8
     * <p>必须配 {@code Range: npt=now-}：海康/大华/宇视等设备收到单发 SCALE（无 Range）会忽略或解释成"从头按新倍速播"，
     * 加上 {@code npt=now-} 才表示"从当前位置以新倍速继续"。</p>
     * <pre>
     * PLAY RTSP/1.0
     * CSeq: 712345681
     * Range: npt=now-
     * Scale: 2.000000
     * </pre>
     */
    public static String scale(double speed) {
        // 强制 Locale.US：避免 zh_CN 默认 locale 输出 "1,000000" 被设备拒绝
        return "PLAY RTSP/1.0\r\n"
                + "CSeq: " + nextCseq() + "\r\n"
                + "Range: npt=now-\r\n"
                + "Scale: " + String.format(Locale.US, "%.6f", speed) + "\r\n\r\n";
    }

    /**
     * RTSP body 自己的 CSeq（跟 SIP CSeq 是两码事；SIP CSeq 在 INFO 头部，由 SipRequestHeaderProvider 管理）。
     * 9 位随机数。
     */
    private static int nextCseq() {
        return ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999);
    }
}
