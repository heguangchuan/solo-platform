package com.solo.ai.request;

import lombok.Data;

/**
 * 创建会话请求体
 *
 * <p>对应 {@code POST /api/ai/chat/conversations}。会话类型决定了后续能调的服务能力：</p>
 * <ul>
 *   <li>{@code CHAT} — 普通对话会话，仅能调 {@code /send} / {@code /stream}（对话）</li>
 *   <li>{@code CREATION} — 创作会话（对话 / 生图 / 生视频混合），<b>必须</b>用 {@code /api/ai/creation/stream}</li>
 *   <li>{@code TASK} — 任务型会话（保留类型，当前未启用，预留扩展位）</li>
 * </ul>
 */
@Data
public class ConversationCreateRequest {

    /** 可选；会话标题（前端可在第一次对话后回写更新标题） */
    private String title;

    /** 必填；会话类型：{@code CHAT} 普通对话 / {@code CREATION} 创作会话 / {@code TASK} 任务型（保留） */
    private String conversationType;
}
