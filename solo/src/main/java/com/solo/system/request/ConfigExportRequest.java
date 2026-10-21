package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "参数导出请求")
@EqualsAndHashCode(callSuper = true)
@Data
public class ConfigExportRequest extends ConfigPageRequest {

    @Schema(description = "指定导出的参数 ID 集合")
    private List<Long> ids;
}
