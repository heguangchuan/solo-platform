package com.solo.openapi.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "开放API应用创建/编辑请求")
@Data
public class OpenAppSaveRequest {

    @Schema(description = "应用ID（编辑时必填）")
    private Long id;

    @Schema(description = "应用名称（必填）")
    @NotBlank(message = "应用名称不能为空")
    private String appName;

    @Schema(description = "授权范围（逗号分隔）")
    private String scopes;

    @Schema(description = "IP白名单（逗号分隔）")
    private String ipWhitelist;

    @Schema(description = "Webhook 回调地址")
    private String callbackUrl;

    @Schema(description = "订阅事件类型（逗号分隔）")
    private String eventSubscriptions;

    @Schema(description = "QPS上限，0=不限")
    private Integer qpsLimit;

    @Schema(description = "日调用量上限，0=不限")
    private Long dailyLimit;

    @Schema(description = "状态 0停用 1启用")
    private Integer status;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "备注")
    private String remark;
}
