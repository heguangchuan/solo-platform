package com.solo.ai.llm;

/**
 * 视频生成查询结果。
 */
public record VideoGenerationQueryResult(String taskId, String videoId, String status, Integer progress, String videoUrl, String error, String seconds, String size) {
}
