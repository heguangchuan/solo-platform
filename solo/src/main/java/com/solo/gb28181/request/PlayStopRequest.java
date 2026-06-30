package com.solo.gb28181.request;

import lombok.Data;

/**
 * 停止点播请求
 */
@Data
public class PlayStopRequest {

    /**
     * ZLM stream（约定 {@code <deviceId>_<channelId>}）
     */
    private String streamId;
}
