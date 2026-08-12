package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;
import com.solo.media.entity.MediaServer;

/**
 * INVITE 会话清扫服务：点播 / 回放 / 下载共用的「收尾」逻辑。
 *
 * <p>只负责一件事——把一条 INVITE 会话及其占用的资源按正确顺序拆干净：
 * ZLM 的 RTP 收流端口、SSRC 池槽位、会话记录，必要时再向对端发 BYE。
 * 顺序不可颠倒（先 closeRtpServer 再 release SSRC，否则新会话拿到同一 ssrc 时
 * {@code openRtpServer} 会被 ZLM 拒绝）。</p>
 *
 * <p>四个 fail/timeout 方法的差别只在<b>是否发 BYE</b>与<b>语义</b>，选错会导致设备继续推流：</p>
 * <ul>
 *     <li>{@link #failSession} — INVITE 阶段就失败，对端没有 Dialog，<b>不发 BYE</b></li>
 *     <li>{@link #failAndBye} — 对端已回 200 OK，<b>必须 BYE</b> 才会停推流</li>
 *     <li>{@link #timeoutSession} — 已 200 OK 但流始终没来，先清 ZLM 再 BYE</li>
 *     <li>{@link #forceCleanup} — 残留会话强拆（启动清扫等），不发 BYE</li>
 * </ul>
 *
 * <p>对 sessionType（PLAY / PLAYBACK / DOWNLOAD）无关，纯工程性清扫，不含任何业务编排。</p>
 */
public interface GbInviteSessionCleanupService {

    /**
     * 申请 RTP 收流端口；若 ZLM 报 "stream already exists"，先 closeRtpServer 再开一次。
     *
     * <p><b>不把 ssrc 下发给 ZLM</b>，即不按 SSRC 过滤收流：本平台固定多端口收流
     * （每路流独占一个 RTP 端口），靠端口就能唯一定位流，过滤只会在下级自定义 SSRC 时把包丢掉。
     * 本地分配的 ssrc 仍会写进 INVITE 的 {@code y=} 行正常宣告，只是不作为 ZLM 的收流条件。
     * 详见 gb28181.md ADR-025。</p>
     *
     * @param tcpMode 0=UDP，1=TCP-passive，2=TCP-active
     * @return ZLM 实际分配的收流端口
     */
    int openRtpServerWithRetry(MediaServer server, String streamId, int tcpMode);

    /**
     * 关 ZLM 的 RTP 端口 + 释放 SSRC。
     * <p>顺序严格：先关后释放，避免新会话拿到同 ssrc 时 {@code openRtpServer} 被拒。</p>
     */
    void cleanupZlm(GbInviteSession session);

    /**
     * 强制清扫一条残留会话（启动清扫 / 业务侧主动放弃）：关 ZLM 端口 → 释放 SSRC → 删会话。
     * <p>不发 BYE（对端多半已走完 Dialog 生命周期）。</p>
     */
    void forceCleanup(GbInviteSession session);

    /**
     * 失败放弃：清扫但<b>不发 BYE</b>（INVITE 都没成功，对端没有 Dialog）。
     */
    void failSession(GbInviteSession session, String logTag, String reason);

    /**
     * 主动放弃 + 发 BYE（对端已收过 200 OK，必须 BYE 才会停推流）。
     */
    void failAndBye(GbInviteSession session, String logTag, String reason);

    /**
     * 等流超时（INVITE 已 200 OK 但流没来）：先清 ZLM 再 BYE。
     */
    void timeoutSession(GbInviteSession session, String logTag, String reason);
}
