package com.solo.media.response;

import lombok.Data;

/**
 * 拉流代理播放地址响应
 * <p>{@code status=1} 时才会有 URL，否则字段为空。</p>
 */
@Data
public class StreamProxyPlayUrlResponse {

    private String app;
    private String stream;
    private String mediaServerId;
    private String flvUrl;
    private String wsFlvUrl;
    private String hlsUrl;
    private String rtcUrl;
}
