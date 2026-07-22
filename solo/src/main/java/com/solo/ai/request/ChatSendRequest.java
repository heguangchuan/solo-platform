package com.solo.ai.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

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
    private Map<String, Object> variables;

    /**
     * 多模态图片输入：每张图给出可访问 URL，发给模型并落库 ai_asset
     */
    private List<AttachmentInput> images;

    @Data
    public static class AttachmentInput {
        /** 图片访问 URL，必填，需公开可达 */
        private String url;
        /** 文件原名，可选 */
        private String fileName;
        /** MIME 类型，可选，例如 image/png */
        private String mimeType;
    }
}
