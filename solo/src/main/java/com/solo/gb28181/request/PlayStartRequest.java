package com.solo.gb28181.request;

import lombok.Data;

/**
 * 实时点播开始请求
 * <p>controller 不直接接 deviceId/channelId 多参，统一封装到本类，
 * 后续若需要扩展（如指定 mediaServerId、subStream 子码流），扩字段即可。</p>
 */
@Data
public class PlayStartRequest {

    /**
     * 设备国标 ID（20 位）
     */
    private String deviceId;

    /**
     * 通道国标 ID（20 位）
     */
    private String channelId;

    /**
     * 收流模式：{@code UDP} / {@code TCP-passive}；缺省 {@code UDP}
     */
    private String transport;
}
