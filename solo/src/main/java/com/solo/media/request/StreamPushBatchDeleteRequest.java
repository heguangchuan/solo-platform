package com.solo.media.request;

import lombok.Data;

import java.util.List;

/**
 * 推流批量删除请求
 *
 * <p>对应 {@code POST /api/stream-push/batch/delete}。
 * 用于一次性清理多条离线推流记录（推流已中止、但表里还有残留行）。</p>
 */
@Data
public class StreamPushBatchDeleteRequest {

    /** 推流主键 ID 列表（必填；可任意长度） */
    private List<Long> ids;
}
