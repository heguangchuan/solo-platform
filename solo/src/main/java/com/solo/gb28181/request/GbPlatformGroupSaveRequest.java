package com.solo.gb28181.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "新建/编辑上级平台推送分组请求")
@Data
public class GbPlatformGroupSaveRequest {

    @Schema(description = "分组名称（必填）")
    @NotBlank(message = "分组名称不能为空")
    private String name;

    @Schema(description = "分组国标编码（必填，20 位数字；前 10 位 = 本平台 domain，第 11-13 位 = 业务分类 215=业务分组 / 216=虚拟组织，后 7 位 = 序号；平台内唯一）")
    @NotBlank(message = "分组编码不能为空")
    @Pattern(regexp = "^\\d{10}(215|216)\\d{7}$", message = "分组编码必须是 20 位数字，且第 11-13 位为 215（业务分组）或 216（虚拟组织）")
    private String code;

    @Schema(description = "父分组主键（根 = 0）")
    @NotNull(message = "父分组不能为空，根节点请填 0")
    private Long parentId;
}
