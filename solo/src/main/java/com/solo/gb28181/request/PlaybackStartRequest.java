package com.solo.gb28181.request;

import lombok.Data;

/**
 * 开始回放请求
 */
@Data
public class PlaybackStartRequest {

    private String deviceId;

    private String channelId;

    /** 起始时间 {@code yyyy-MM-dd HH:mm:ss} */
    private String startTime;

    /** 结束时间 {@code yyyy-MM-dd HH:mm:ss} */
    private String endTime;

    /** 收流模式：{@code UDP} / {@code TCP-passive}；缺省 UDP */
    private String transport;
}
