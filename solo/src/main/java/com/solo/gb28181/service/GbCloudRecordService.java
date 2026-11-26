package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;

/**
 * 云端录制会话编排：负责把设备流用 RECORD 类型的 INVITE 拉进 ZLM 并开 MP4 录制。
 * 切片登记由 {@link GbRecordFileService} 负责，本接口只管“流的生命周期”。
 */
public interface GbCloudRecordService {

    /** 码流 token 归一：冒号在 ZLM 流名里非法 */
    static String normalizeToken(String streamNumber) {
        if (streamNumber == null || streamNumber.isBlank()) {
            return null;
        }
        return streamNumber.trim().replace(":", "_");
    }

    static String manualStreamId(String deviceId, String channelId, String streamNumber) {
        String base = "REC_" + deviceId + "_" + channelId;
        String token = normalizeToken(streamNumber);
        return token == null ? base : base + "_" + token;
    }

    /**
     * 手动录制：立即拉起一路流开始录。streamId 由设备/通道/码流决定，已在录直接复用。
     *
     * @return 会话对应的 ZLM streamId
     */
    String startManual(String deviceId, String channelId, String transport, String streamNumber);

    /** 手动停止：发 BYE + 清 ZLM/SSRC/会话（ZLM 会先把最后一段切出来回调 on_record_mp4） */
    void stop(String streamId);

    /** on_stream_changed(regist=true)：流进 ZLM 了，开 startRecord */
    void onStreamReady(String app, String stream, String mediaServerId);

    /**
     * 对账补偿：READY 会话不代表 ZLM 真的在录（外部手动 stopRecord / ZLM 异常丢录制态都无事件）。
     * 查 ZLM 真实录制态，流在但没录就补发 startRecord。
     *
     * @return true=本轮执行了补发；false=在录 / 状态不明 / 流已不存在（流不存在的会话转 STOPPED 交给对账重拉）
     */
    boolean ensureRecording(GbInviteSession session);

    /** on_stream_changed(regist=false)：流没了，清理会话（BYE 不一定发得出去） */
    void onStreamStopped(String app, String stream, String mediaServerId);

    /** on_rtp_server_timeout */
    void onRtpServerTimeout(String streamId);

    /** 设备主动 BYE */
    void onDeviceBye(GbInviteSession session);
}
