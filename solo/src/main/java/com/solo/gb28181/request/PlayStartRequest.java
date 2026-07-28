package com.solo.gb28181.request;

import lombok.Data;

/**
 * 实时点播开始请求
 * <p>controller 不直接接 deviceId/channelId 多参，统一封装到本类，
 * 后续若需要扩展（如指定 mediaServerId），扩字段即可。</p>
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
    private String transport = "TCP-passive";

    /**
     * 码流标识 token：留空 / null 表示主码流（不改报文），子码流传厂商约定的 wire token。
     * <p>取非空时，编排层会把它原样附加到发往设备的 INVITE 的 Request-URI / To / Subject
     * 通道段后缀（{@code sip:<channelId>:<token>@host}）。token 由前端 / 配置给出，
     * 后端不解析其含义，以此兼容各厂商写法（如 {@code 1} / {@code stream:sub} / {@code streamnumber:1}）。</p>
     */
    private String streamNumber = "streamnumber:1";
}
