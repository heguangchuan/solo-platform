package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "发送消息请求")
@Data
public class SendMessageRequest {

    @Schema(description = "目标用户名")
    private String username;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型：SYSTEM/NOTICE/REMINDER/BROADCAST，缺省 NOTICE")
    private String type = "NOTICE";

    @Schema(description = "消息级别：INFO/WARNING/ERROR/IMPORTANT，缺省 INFO")
    private String level = "INFO";

    @Schema(description = "跳转路由")
    private String route;
}
