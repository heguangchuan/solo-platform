package com.solo.ai.service;

import com.solo.ai.entity.AiModelConfig;

/**
 * AI 意图路由服务：判定一轮用户消息应走 对话 / 生图 / 生视频。
 */
public interface AiIntentService {

    /**
     * 结合会话上下文对用户消息做意图分类。解析失败时兜底返回 CHAT。
     */
    IntentResult classify(Long conversationId, String userMessage, AiModelConfig textModel);

    /**
     * 意图分类结果。
     *
     * @param intent       CHAT / IMAGE / VIDEO
     * @param useLastImage 是否基于会话里最近一张生成图继续创作
     * @param prompt       用于生图/生视频的提示词（CHAT 时等于原文）
     */
    record IntentResult(String intent, boolean useLastImage, String prompt) {
        public static IntentResult chat(String userMessage) {
            return new IntentResult("CHAT", false, userMessage);
        }
    }
}
