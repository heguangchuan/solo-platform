package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户导出请求")
@Data
public class UserExportRequest extends UserPageRequest {

    @Schema(description = "指定导出的用户 ID 集合")
    private List<Long> ids;
}
