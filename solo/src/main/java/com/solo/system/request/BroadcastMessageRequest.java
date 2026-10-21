package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "广播消息请求")
@Data
public class BroadcastMessageRequest {

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息级别：INFO/WARNING/ERROR/IMPORTANT，缺省 INFO")
    private String level = "INFO";
}
