package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典类型新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/dict/type}、{@code PUT /api/dict/type/{id}}。</p>
 */
@Schema(description = "字典类型新增/编辑请求体")
@Data
public class DictTypeRequest {

    /** 字典类型名称（必填，管理用） */
    @Schema(description = "字典类型名称")
    private String dictName;

    /** 字典类型编码（必填，全局唯一；业务侧按此编码引用） */
    @Schema(description = "字典类型编码")
    private String dictType;

    /** 状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;
}
