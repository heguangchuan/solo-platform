package com.solo.gb28181.request;

import lombok.Data;

/**
 * 停止回放请求
 *
 * <p>对应 {@code POST /api/gb28181/playback/stop}。</p>
 */
@Data
public class PlaybackStopRequest {

    /** 流 ID（回放 start 响应中返回） */
    private String streamId;
}
