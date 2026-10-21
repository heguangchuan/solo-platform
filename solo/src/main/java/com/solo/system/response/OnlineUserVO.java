package com.solo.system.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(description = "在线用户响应体")
@Data
public class OnlineUserVO {

    @Schema(description = "用户 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime loginTime;

    @Schema(description = "登录 IP")
    private String loginIp;

    @Schema(description = "客户端来源")
    private String clientSource;

    @Schema(description = "浏览器 User-Agent")
    private String userAgent;
}
