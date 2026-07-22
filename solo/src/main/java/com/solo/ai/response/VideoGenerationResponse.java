package com.solo.ai.response;

import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 视频生成响应。
 */
@Data
public class VideoGenerationResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    private String modelName;
    private String status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long assetId;

    private String videoUrl;
    private String fileName;
}
