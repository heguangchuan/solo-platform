package com.solo.request;

import lombok.Data;

/**
 * 发送消息请求
 */
@Data
public class ChatSendRequest {

    /**
     * 会话ID（新建会话时为空）
     */
    private Long conversationId;

    /**
     * 用户消息内容
     */
    private String content;

    /**
     * 使用的模型配置ID，为空时使用默认文本模型
     */
    private Long modelConfigId;

    /**
     * 模板编码（使用模板时指定）
     */
    private String templateCode;

    /**
     * 模板变量
     */
    private java.util.Map<String, Object> variables;
}
