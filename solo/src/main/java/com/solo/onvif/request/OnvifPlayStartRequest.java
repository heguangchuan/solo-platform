package com.solo.onvif.request;

import lombok.Data;

/**
 * ONVIF 点播开始请求
 */
@Data
public class OnvifPlayStartRequest {

    /** ONVIF 设备主键 */
    private Long deviceId;

    /** 码流类型：0 主码流 1 子码流；不传按设备配置的 streamType */
    private Integer streamType;
}
