package com.solo.onvif.request;

import lombok.Data;

/**
 * ONVIF 点播停止请求
 */
@Data
public class OnvifPlayStopRequest {

    /** ZLM stream 名（约定 {@code onvif_<deviceId>}） */
    private String streamId;
}
