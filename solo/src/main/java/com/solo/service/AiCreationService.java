package com.solo.service;

/**
 * 统一创作编排服务：在同一个 MIXED 会话里，按意图路由到 对话 / 生图 / 生视频。
 */
public interface AiCreationService {

    /**
     * 处理一轮统一创作消息。
     *
     * @param conversationId 会话ID，为空时新建 MIXED 会话
     * @param content        用户原始输入
     * @param images         用户提供的图片 URL（上传或素材库），可为空
     * @param userId         当前用户
     * @param modelConfigId  文本模型配置ID（用于意图识别与对话），为空用默认文本模型
     * @param sseConsumer    文本增量/占位文字的下发回调
     */
    CreationResult send(Long conversationId, String content, java.util.List<String> images, Long userId,
                        Long modelConfigId, java.util.function.Consumer<String> sseConsumer);

    /**
     * 编排结果。
     *
     * @param conversationId 会话ID
     * @param type           本轮类型 CHAT / IMAGE / VIDEO
     * @param taskId         生成任务ID（CHAT 时为 null）
     * @param reply          对话回复或占位文字
     */
    record CreationResult(Long conversationId, String type, Long taskId, String reply) {}
}
