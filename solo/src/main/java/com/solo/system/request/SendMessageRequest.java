package com.solo.system.request;

import lombok.Data;

/**
 * 发送消息请求
 *
 * <p>对应接口 {@code POST /api/message/send}。在线用户走 WebSocket 实时推送，
 * 离线用户落库等下次登录由 {@code GET /api/message/list} 拉取。</p>
 *
 * <h3>字段取值</h3>
 * <ul>
 *   <li>{@code type} — {@code SYSTEM} / {@code NOTICE} / {@code REMINDER} / {@code BROADCAST}，缺省 {@code NOTICE}</li>
 *   <li>{@code level} — {@code INFO} / {@code WARNING} / {@code ERROR} / {@code IMPORTANT}，缺省 {@code INFO}</li>
 * </ul>
 */
@Data
public class SendMessageRequest {

    /** 目标用户名（必填） */
    private String username;

    /** 消息标题（必填） */
    private String title;

    /** 消息内容（必填） */
    private String content;

    /** 消息类型：{@code SYSTEM}/{@code NOTICE}/{@code REMINDER}/{@code BROADCAST}，缺省 {@code NOTICE} */
    private String type = "NOTICE";

    /** 消息级别：{@code INFO}/{@code WARNING}/{@code ERROR}/{@code IMPORTANT}，缺省 {@code INFO} */
    private String level = "INFO";

    /** 跳转路由（可选，前端收到后跳转） */
    private String route;
}
