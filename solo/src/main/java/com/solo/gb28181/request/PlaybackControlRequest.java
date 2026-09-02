package com.solo.gb28181.request;

import lombok.Data;

/**
 * 回放控制请求
 *
 * <p>对应 {@code POST /api/gb28181/playback/control}。</p>
 *
 * <p>四种动作共享一个 DTO，{@link #param} 字段语义按 {@link #action} 不同：</p>
 * <ul>
 *   <li>{@code pause} / {@code resume} — 不需要 param</li>
 *   <li>{@code seek} — param 为相对起点的秒数（{@code "60"} 表示跳到第 60 秒）</li>
 *   <li>{@code speed} — param 为倍速（{@code "0.5"}/{@code "1"}/{@code "2"}/{@code "4"}）</li>
 * </ul>
 */
@Data
public class PlaybackControlRequest {

    /** 流 ID（回放 start 响应中返回） */
    private String streamId;

    /** 动作类型：{@code pause} / {@code resume} / {@code seek} / {@code speed} */
    private String action;

    /** 按 action 不同含义不同；{@code pause}/{@code resume} 时可省 */
    private String param;
}
