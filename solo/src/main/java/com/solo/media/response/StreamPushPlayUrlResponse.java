package com.solo.media.response;

import lombok.Data;

/**
 * 推流播放地址响应
 */
@Data
public class StreamPushPlayUrlResponse {
    private String app;
    private String stream;
    private String mediaServerId;
    private String flvUrl;
    private String wsFlvUrl;
    private String hlsUrl;
    private String rtcUrl;
}
