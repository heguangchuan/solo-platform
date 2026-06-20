package com.solo.response;

import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 图片生成响应。
 */
@Data
public class ImageGenerationResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    private String modelName;
    private String status;
    private List<ImageItem> images;

    @Data
    public static class ImageItem {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long assetId;
        private String url;
        private String fileName;
    }
}
