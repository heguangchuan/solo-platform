package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 参数新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/config}、{@code PUT /api/config/{id}}。
 * 业务通过 {@code configKey} 在运行期读取 {@code configValue}。</p>
 */
@Schema(description = "参数新增/编辑请求体")
@Data
public class ConfigRequest {

    /** 参数名称（必填，管理用） */
    @Schema(description = "参数名称")
    private String configName;

    /** 参数编码（必填，全局唯一；业务侧按此 key 读取） */
    @Schema(description = "参数编码")
    private String configKey;

    /** 参数取值（必填） */
    @Schema(description = "参数取值")
    private String configValue;

    /** 备注（可选） */
    @Schema(description = "备注")
    private String remark;
}
