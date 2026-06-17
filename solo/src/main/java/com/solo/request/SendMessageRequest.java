package com.solo.request;

import lombok.Data;

/**
 * 发送消息请求
 */
@Data
public class SendMessageRequest {
    /**
     * 目标用户名
     */
    private String username;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型：SYSTEM/NOTICE/REMINDER/BROADCAST
     */
    private String type = "NOTICE";

    /**
     * 消息级别：INFO/WARNING/ERROR/IMPORTANT
     */
    private String level = "INFO";

    /**
     * 跳转路由（可选）
     */
    private String route;
}
