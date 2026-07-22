package com.solo.ai.request;

import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class ConversationCreateRequest {

    /**
     * 会话标题
     */
    private String title;

    /**
     * 会话类型: CHAT/CREATION/TASK
     */
    private String conversationType;
}
