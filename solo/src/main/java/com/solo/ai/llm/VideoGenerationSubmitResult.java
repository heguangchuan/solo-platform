package com.solo.ai.llm;

/**
 * 视频生成任务创建结果。
 */
public record VideoGenerationSubmitResult(String taskId, String videoId, String status, String seconds, String size) {
}
