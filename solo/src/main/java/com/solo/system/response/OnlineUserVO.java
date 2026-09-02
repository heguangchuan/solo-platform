package com.solo.system.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 在线用户响应体（不含敏感的 token 原文）
 *
 * <p>对应接口 {@code GET /api/online/list}。
 * 同一用户多次登录会出现多条记录（不同 token / IP）。access 过期（30 分钟无活动）后自动从列表消失。</p>
 */
@Data
public class OnlineUserVO {

    /** 用户 ID（字符串形式，规避前端大数精度问题） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 登录时间（{@code yyyy-MM-dd HH:mm:ss}，GMT+8） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime loginTime;

    /** 登录 IP */
    private String loginIp;

    /** 客户端来源（{@code web} / {@code app}） */
    private String clientSource;

    /** 浏览器 User-Agent（前端可做简单浏览器 / 系统识别） */
    private String userAgent;
}
