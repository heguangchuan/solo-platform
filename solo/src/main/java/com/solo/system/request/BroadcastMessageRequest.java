package com.solo.system.request;

import lombok.Data;

/**
 * 广播消息请求
 *
 * <p>对应接口 {@code POST /api/message/broadcast}，向所有 WebSocket 在线用户推送。
 * 离线用户<b>不会</b>收到——广播不带离线落库，仅在用户在线时实时弹出。</p>
 */
@Data
public class BroadcastMessageRequest {

    /** 消息标题（必填） */
    private String title;

    /** 消息内容（必填） */
    private String content;

    /** 消息级别：{@code INFO}/{@code WARNING}/{@code ERROR}/{@code IMPORTANT}，缺省 {@code INFO} */
    private String level = "INFO";
}
