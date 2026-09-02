package com.solo.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.ai.entity.AiConversation;
import com.solo.ai.entity.AiMessage;
import com.solo.ai.request.ChatSendRequest;
import com.solo.ai.request.ConversationCreateRequest;
import com.solo.ai.service.AiChatService;
import com.solo.ai.service.AiConversationService;
import com.solo.ai.service.AiMessageService;
import com.solo.aop.RequirePermission;
import com.solo.common.PageResult;
import com.solo.common.Result;
import com.solo.common.utils.UserHolderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 对话（聊天）
 *
 * <p>前端"对话式 AI 助手"页用这套接口：每个登录用户可建多个会话（{@code CHAT} / {@code CREATION} 混合），
 * 会话下挂消息列表；调对话模型（OpenAI / DeepSeek / Kimi / 通义千问 / 智谱 / Anthropic / Gemini / 自定义兼容 等）
 * 拿回复。支持多模态图片输入。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /api/ai/chat/send} — 同步发送消息，一次性返回完整回复（无流式）</li>
 *   <li>{@code POST /api/ai/chat/stream} — 流式发送消息（SSE，逐 token 输出，详见方法注释）</li>
 *   <li>{@code GET /api/ai/chat/conversations} — 当前登录用户的会话列表（分页）</li>
 *   <li>{@code GET /api/ai/chat/conversations/{conversationId}/messages} — 会话消息列表</li>
 *   <li>{@code POST /api/ai/chat/conversations} — 创建新会话</li>
 *   <li>{@code DELETE /api/ai/chat/conversations/{conversationId}} — 删除会话（关联消息一并清理）</li>
 *   <li>{@code PUT /api/ai/chat/conversations/{conversationId}/title} — 修改会话标题</li>
 * </ul>
 *
 * <h3>归属 / 越权</h3>
 * <p>所有接口只返回当前登录用户自己的会话与消息；尝试访问别人的会话 id 时，消息列表接口会返回空数组，
 * 不是 403 错误——前端可以在 UI 上把空列表展示为"暂无消息"或"该会话不可访问"。</p>
 */
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final AiChatService aiChatService;
    private final AiConversationService conversationService;
    private final AiMessageService messageService;

    private static final ExecutorService SSE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable, "ai-chat-stream");
        t.setDaemon(true);
        return t;
    });

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 发送消息（同步，一次性返回完整回复）
     * <p>给"短回复 / 不在意逐字渲染"的场景用。如果要"打字机效果"，请改用流式接口 {@code /stream}。</p>
     *
     * <p>分支规则：
     * <ul>
     *   <li>{@code templateCode} 非空 → 走提示词模板对话（变量替换 + 渲染后调 LLM）</li>
     *   <li>{@code templateCode} 为空 → 普通对话（直接调 LLM）</li>
     * </ul>
     * </p>
     *
     * <p>超时：模型厂商通常 30s 内能返回；如果超过 60s 仍无回复，前端要主动取消请求并提示用户重试。</p>
     */
    @PostMapping("/send")
    @RequirePermission({"ai:conversation:send"})
    public Result<String> send(@RequestBody ChatSendRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();

        String response;
        if (request.getTemplateCode() != null && !request.getTemplateCode().isEmpty()) {
            // 使用模板
            response = aiChatService.chatWithTemplate(request.getConversationId(), request.getTemplateCode(), request.getVariables(), userId, request.getModelConfigId());
        } else {
            // 普通对话
            response = aiChatService.chat(request.getConversationId(), request.getContent(), userId, request.getModelConfigId());
        }

        return Result.success(response);
    }

    /**
     * 流式发送消息（SSE，前端用 EventSource 或 fetch 接收逐 token 输出）
     *
     * <p>请求体同 {@code /send}；响应 {@code Content-Type: text/event-stream}，前端三种常用接法：
     * <ul>
     *   <li>EventSource（POST 模式需用 {@code @microsoft/fetch-event-source} 等 polyfill，原生 EventSource 不支持 POST）</li>
     *   <li>fetch + {@code response.body.getReader()} 解析 SSE 文本流</li>
     *   <li>axios + {@code onDownloadProgress}（不推荐，丢帧、无法区分事件）</li>
     * </ul>
     * </p>
     *
     * <h3>SSE 事件清单</h3>
     * <ul>
     *   <li>{@code chunk} — 文本增量；data 是字符串片段，前端直接追加到消息气泡末尾</li>
     *   <li>{@code done} — 结束；data 是 JSON：{@code {"conversationId":"...","reply":"..."}}，
     *       {@code conversationId} 用于本条消息入库后定位，{@code reply} 是最终完整回复（冗余备份）</li>
     *   <li>{@code error} — 异常；data 是错误描述字符串，前端显示在气泡内并停止光标动画</li>
     * </ul>
     *
     * <p>分支：{@code templateCode} 非空 → 模板对话一次性返回（只发一个 {@code chunk} + {@code done}，
     * 无逐 token 效果）；否则走对话模型的流式增量。</p>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission({"ai:conversation:send"})
    public SseEmitter stream(@RequestBody ChatSendRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(0L);

        SSE_EXECUTOR.execute(() -> {
            try {
                String content = request.getContent();
                if (request.getTemplateCode() != null && !request.getTemplateCode().isEmpty()) {
                    content = aiChatService.chatWithTemplate(request.getConversationId(), request.getTemplateCode(), request.getVariables(), userId, request.getModelConfigId());
                    emitter.send(SseEmitter.event().name("chunk").data(content));
                    emitter.send(SseEmitter.event().name("done").data(buildDonePayload(
                            request.getConversationId() == null ? "" : request.getConversationId().toString(),
                            content)));
                    emitter.complete();
                    return;
                }

                AiChatService.StreamResult result = aiChatService.streamChat(
                        request.getConversationId(),
                        content,
                        request.getImages(),
                        userId,
                        request.getModelConfigId(),
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().name("chunk").data(chunk));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                emitter.send(SseEmitter.event().name("done").data(buildDonePayload(
                        String.valueOf(result.conversationId()),
                        result.reply())));
                emitter.complete();
            } catch (Exception e) {
                log.warn("流式对话失败: {}", e.getMessage(), e);
                String message = e.getMessage() == null ? "对话失败" : e.getMessage();
                try {
                    emitter.send(SseEmitter.event().name("error").data(message));
                } catch (IOException ignore) {
                }
                // 注意：异常发完后直接 complete()，不要用 completeWithError，
                // 否则 Spring 会把请求重新调度走 ExceptionHandler，最终用 SSE 的
                // Content-Type 写 Result，触发 No converter for ... 错误。
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * SSE 的 done 事件数据用 JSON 字符串，避免 Spring 在 text/event-stream 下找不到 Map 的转换器。
     */
    private String buildDonePayload(String conversationId, String reply) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "conversationId", conversationId == null ? "" : conversationId,
                    "reply", reply == null ? "" : reply));
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 获取当前用户的会话列表
     *
     * <p>URL 参数：
     * <ul>
     *   <li>{@code page} — 页码，从 1 开始，默认 1</li>
     *   <li>{@code pageSize} — 每页条数，默认 20</li>
     *   <li>{@code conversationType} — 可选过滤；{@code CHAT}（普通对话）/
     *       {@code CREATION}（创作会话，对话+生图+生视频混合）/{@code TASK}（任务型）</li>
     * </ul>
     * 排序：最近活跃的会话排前。</p>
     */
    @GetMapping("/conversations")
    @RequirePermission({"ai:conversation:list", "ai:imageCreation:list", "ai:videoCreation:list", "ai:creation:list"})
    public Result<PageResult<AiConversation>> getConversations(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String conversationType) {
        Long userId = UserHolderUtils.getCurrentUserId();
        Page<AiConversation> pageResult = conversationService.getUserConversations(userId, conversationType, page, pageSize);
        return Result.success(new PageResult<>(pageResult.getTotal(), pageResult.getRecords()));
    }

    /**
     * 获取会话消息列表
     *
     * <p>返回该会话下的所有消息（含用户问 / AI 答 / 多模态图片消息），按时间升序。
     * 越权保护：会话不属于当前用户、或会话已被删时，<b>返回空列表而不是 404 / 403</b>，
     * 前端按空列表渲染即可，不要去弹错误提示。</p>
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @RequirePermission({"ai:conversation:list", "ai:imageCreation:list", "ai:videoCreation:list", "ai:creation:list"})
    public Result<List<AiMessage>> getMessages(@PathVariable Long conversationId) {
        Long userId = UserHolderUtils.getCurrentUserId();
        AiConversation conversation = conversationService.getById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return Result.success(List.of());
        }
        List<AiMessage> messages = messageService.getConversationMessages(conversationId);
        return Result.success(messages);
    }

    /**
     * 创建新会话
     *
     * <p>请求体取 {@code conversationType}（必填：{@code CHAT} / {@code CREATION} / {@code TASK}）、
     * {@code title}（可选，留空时由系统生成默认标题）。响应返回完整会话对象（含 id），
     * 前端拿到 id 后立即可调 {@code /send} 或 {@code /stream} 发消息。</p>
     */
    @PostMapping("/conversations")
    @RequirePermission({"ai:conversation:create", "ai:imageCreation:generate", "ai:videoCreation:generate", "ai:creation:create"})
    public Result<AiConversation> createConversation(@RequestBody ConversationCreateRequest request) {
        Long userId = UserHolderUtils.getCurrentUserId();
        AiConversation conversation = conversationService.createConversation(userId, request.getConversationType(), request.getTitle());
        return Result.success(conversation);
    }

    /**
     * 删除会话
     * <p>会话底下的所有消息会一并删除，不可恢复；前端删除前应弹二次确认。</p>
     */
    @DeleteMapping("/conversations/{conversationId}")
    @RequirePermission({"ai:conversation:delete", "ai:imageCreation:delete", "ai:videoCreation:delete", "ai:creation:delete"})
    public Result<Void> deleteConversation(@PathVariable Long conversationId) {
        Long userId = UserHolderUtils.getCurrentUserId();
        conversationService.deleteConversation(conversationId, userId);
        return Result.success();
    }

    /**
     * 修改会话标题
     * <p>用户在会话列表上重命名时调一次（{@code title} 走 query string）。</p>
     */
    @PutMapping("/conversations/{conversationId}/title")
    @RequirePermission({"ai:conversation:edit"})
    public Result<Void> updateTitle(@PathVariable Long conversationId, @RequestParam String title) {
        conversationService.updateTitle(conversationId, title);
        return Result.success();
    }
}
