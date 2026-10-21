package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "角色导出请求")
@Data
public class RoleExportRequest extends RolePageRequest {

    @Schema(description = "指定导出的角色 ID 集合")
    private List<Long> ids;
}
