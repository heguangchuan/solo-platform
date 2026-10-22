package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;

/**
 * 云端录制会话编排：负责把设备流用 RECORD 类型的 INVITE 拉进 ZLM 并开 MP4 录制。
 * 切片落 S3 的活由 {@link GbRecordFileService} 干，本接口只管"流的生命周期"。
 */
public interface GbCloudRecordService {

    /** 计划流名前缀，和点播裸 streamId 区分，边录边看互不抢流 */
    String REC_PREFIX = "REC_";

    /** 码流 token 归一：冒号在 ZLM 流名里非法 */
    static String normalizeToken(String streamNumber) {
        if (streamNumber == null || streamNumber.isBlank()) {
            return null;
        }
        return streamNumber.trim().replace(":", "_");
    }

    static String planStreamId(String deviceId, String channelId, String streamNumber) {
        return REC_PREFIX + manualStreamId(deviceId, channelId, streamNumber);
    }

    static String manualStreamId(String deviceId, String channelId, String streamNumber) {
        String base = deviceId + "_" + channelId;
        String token = normalizeToken(streamNumber);
        return token == null ? base : base + "_" + token;
    }

    /**
     * 手动录制：立即拉起一路流开始录。streamId 由设备/通道/码流决定，已在录直接复用。
     *
     * @return 会话对应的 ZLM streamId
     */
    String startManual(String deviceId, String channelId, String transport, String streamNumber);

    /**
     * 计划录制：语义同手动，但流名带 REC 前缀，避免和实时点播抢同一个 ZLM 流。
     */
    String startPlan(String deviceId, String channelId, String transport, String streamNumber);

    /** 手动停止：发 BYE + 清 ZLM/SSRC/会话（ZLM 会先把最后一段切出来回调 on_record_mp4） */
    void stop(String streamId);

    /** 停掉某计划通道当前在录的流（计划停用/删除/改时段），没在录则无操作 */
    void stopPlanRecord(String deviceId, String channelId, String streamNumber);

    /** on_stream_changed(regist=true)：流进 ZLM 了，开 startRecord */
    void onStreamReady(String app, String stream, String mediaServerId);

    /** on_stream_changed(regist=false)：流没了，清理会话（BYE 不一定发得出去） */
    void onStreamStopped(String app, String stream, String mediaServerId);

    /** on_rtp_server_timeout */
    void onRtpServerTimeout(String streamId);

    /** 设备主动 BYE */
    void onDeviceBye(GbInviteSession session);
}
