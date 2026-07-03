package com.solo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.solo.entity.AiMessage;
import com.solo.entity.AiModelConfig;
import com.solo.llm.HttpClientHolder;
import com.solo.llm.LlmClient;
import com.solo.llm.LlmClientRegistry;
import com.solo.service.AiChatService;
import com.solo.service.AiIntentService;
import com.solo.service.AiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 意图路由实现：用文本模型做一次轻量分类调用，只要求返回 JSON。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiIntentServiceImpl implements AiIntentService {

    private static final String SYSTEM_PROMPT = """
            你是一个多模态创作助手的意图路由器。根据对话上下文和用户最新消息，判断这一轮应该执行的动作。
            只能返回一个 JSON 对象，不要输出任何解释、不要用 markdown 代码块包裹。JSON 结构：
            {"intent":"CHAT|IMAGE|VIDEO","useLastImage":true|false,"prompt":"用于生成的提示词"}
            规则：
            - 用户想要一张图片/画作/插画/海报等静态图像时 intent=IMAGE。
            - 用户想要一段视频/动画/短片时 intent=VIDEO。
            - 其余（普通问答、闲聊、修改文字等）intent=CHAT。
            - 当用户说“把这张图…/基于刚才的图/给它加…/用这张图生成视频”等，表示要在上一张生成图基础上继续创作时 useLastImage=true，否则 false。
            - prompt 字段：intent 为 IMAGE/VIDEO 时，整理成清晰、完整、适合生成模型的中文提示词（可结合上下文补全指代）；intent 为 CHAT 时，原样返回用户消息。
            """;

    private final LlmClientRegistry llmClientRegistry;
    private final AiMessageService messageService;

    @Override
    public IntentResult classify(Long conversationId, String userMessage, AiModelConfig textModel) {
        if (textModel == null) {
            return IntentResult.chat(userMessage);
        }
        try {
            List<AiChatService.ChatMessage> messages = new ArrayList<>();
            messages.add(AiChatService.ChatMessage.text("system", SYSTEM_PROMPT));
            if (conversationId != null) {
                for (AiMessage history : messageService.getRecentMessages(conversationId, 6)) {
                    String role = "ASSISTANT".equals(history.getRole()) ? "assistant" : "user";
                    String content = summarizeHistory(history);
                    if (content != null && !content.isBlank()) {
                        messages.add(AiChatService.ChatMessage.text(role, content));
                    }
                }
            }
            messages.add(AiChatService.ChatMessage.text("user", userMessage));

            LlmClient client = llmClientRegistry.resolve(textModel.getProvider());
            String raw = client.chat(textModel, messages);
            return parse(raw, userMessage);
        } catch (Exception e) {
            log.warn("意图识别失败，降级为对话：{}", e.getMessage());
            return IntentResult.chat(userMessage);
        }
    }

    /** 历史里图片/视频消息只给个占位说明，避免把长 JSON 附件塞进上下文 */
    private String summarizeHistory(AiMessage msg) {
        String type = msg.getMessageType();
        if ("IMAGE".equals(type)) return "[已生成图片] " + safe(msg.getContent());
        if ("VIDEO".equals(type)) return "[已生成视频] " + safe(msg.getContent());
        return msg.getContent();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private IntentResult parse(String raw, String userMessage) {
        if (raw == null || raw.isBlank()) return IntentResult.chat(userMessage);
        String json = extractJson(raw);
        if (json == null) return IntentResult.chat(userMessage);
        try {
            JsonNode node = HttpClientHolder.MAPPER.readTree(json);
            String intent = node.path("intent").asText("CHAT").trim().toUpperCase();
            if (!"IMAGE".equals(intent) && !"VIDEO".equals(intent)) {
                intent = "CHAT";
            }
            boolean useLastImage = node.path("useLastImage").asBoolean(false);
            String prompt = node.path("prompt").asText("");
            if (prompt.isBlank()) prompt = userMessage;
            if ("CHAT".equals(intent)) {
                return IntentResult.chat(userMessage);
            }
            return new IntentResult(intent, useLastImage, prompt);
        } catch (Exception e) {
            log.warn("意图 JSON 解析失败，降级为对话：{}", e.getMessage());
            return IntentResult.chat(userMessage);
        }
    }

    /** 从模型输出里截取第一个完整 JSON 对象，兼容模型偶尔带的说明文字或代码块 */
    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return raw.substring(start, end + 1);
    }
}
