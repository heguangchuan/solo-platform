package com.solo.onvif.request;

import lombok.Data;

/**
 * ONVIF 点播开始请求
 */
@Data
public class OnvifPlayStartRequest {

    /** ONVIF 设备主键 */
    private Long deviceId;
}
