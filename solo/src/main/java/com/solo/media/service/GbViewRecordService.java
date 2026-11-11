package com.solo.media.service;

import com.solo.common.PageResult;
import com.solo.media.entity.GbViewRecord;
import com.solo.media.request.GbViewRecordPageRequest;
import com.solo.media.response.GbViewRecordVO;

/**
 * 观看记录：一条对应 ZLM 一个播放会话，on_play 开、on_flow_report 关。
 * 同一路流多人看，会话 id 各不同，各落各的，互不影响。
 */
public interface GbViewRecordService {

    /** flow_report 正常带回了时长和流量 */
    String REASON_FLOW_REPORT = "flow_report";
    /** on_stream_changed(regist=false)：整个流没了，流上的会话一起兜底 */
    String REASON_STREAM_CLOSED = "stream_closed";
    /** on_stream_none_reader：最后一个观看者走了，但没等到 flow_report */
    String REASON_NONE_READER = "none_reader";
    /** 定时清扫：会话僵在观看中，hook 全丢了 */
    String REASON_SWEEP = "sweep";

    /** on_play 落库，按会话 id 幂等，重推只留第一条；status 这里强制置观看中 */
    void openRecord(GbViewRecord record);

    /** flow_report 闭合单条；duration 为空就按 start_time 估算，totalBytes 为空不覆盖 */
    void closeBySessionId(String sessionId, Double durationSec, Long totalBytes, String reason);

    /** 流没了的时候把这条流上观看中的记录全关掉，返回关了几条 */
    int closeOpenByStreamId(String streamId, String reason);

    /** 清扫僵在观看中的记录，staleMinutes 是超时阈值，单轮限量 */
    int sweepOpenRecords(int staleMinutes);

    /** 管理面分页查询，按开始时间倒序，顺带补设备/通道名 */
    PageResult<GbViewRecordVO> page(GbViewRecordPageRequest request);
}
