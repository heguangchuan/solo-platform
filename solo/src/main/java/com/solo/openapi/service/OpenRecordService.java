package com.solo.openapi.service;

import com.solo.openapi.response.OpenRecordStatusResponse;

/** 开放 API 云端录制能力，业务实现与控制器解耦。 */
public interface OpenRecordService {

    /** 发起录制，返回可复用的 ZLM streamId。 */
    String start(String deviceId, String channelId, String transport, String streamNumber);

    /** 停止录制。 */
    void stop(String streamId);

    /** 查询 ZLM 侧真实录制态。 */
    OpenRecordStatusResponse status(String streamId);
}
