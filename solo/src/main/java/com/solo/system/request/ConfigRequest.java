package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "参数新增/编辑请求体")
@Data
public class ConfigRequest {

    @Schema(description = "参数名称")
    private String configName;

    @Schema(description = "参数编码")
    private String configKey;

    @Schema(description = "参数取值")
    private String configValue;

    @Schema(description = "备注")
    private String remark;
}
