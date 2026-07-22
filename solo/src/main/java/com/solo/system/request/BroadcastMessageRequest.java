package com.solo.system.request;

import lombok.Data;

/**
 * 广播消息请求
 */
@Data
public class BroadcastMessageRequest {
    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息级别：INFO/WARNING/ERROR/IMPORTANT
     */
    private String level = "INFO";
}
