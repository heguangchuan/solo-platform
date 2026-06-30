package com.solo.gb28181.request;

import lombok.Data;

/**
 * 点播截图请求
 * <p>仅在 {@link com.solo.gb28181.entity.GbInviteSession} 已经 READY 时可截图。</p>
 */
@Data
public class PlaySnapRequest {

    /**
     * ZLM stream（约定 {@code <deviceId>_<channelId>}）
     */
    private String streamId;
}
