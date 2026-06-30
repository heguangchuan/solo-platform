package com.solo.gb28181.request;

import lombok.Data;

/**
 * 停止回放请求
 */
@Data
public class PlaybackStopRequest {

    /** ZLM stream id（回放约定 {@code <deviceId>_<channelId>_<startCompact>_<endCompact>}） */
    private String streamId;
}
