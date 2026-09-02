package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典数据新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/dict/data}、{@code PUT /api/dict/data/{id}}。</p>
 */
@Schema(description = "字典数据新增/编辑请求体")
@Data
public class DictDataRequest {

    /** 字典类型编码（必填；关联到 {@code sys_dict_type.dictType}） */
    @Schema(description = "字典类型编码")
    private String dictType;

    /** 字典项显示标签（必填，给前端展示用） */
    @Schema(description = "字典项显示标签")
    private String dictLabel;

    /** 字典项实际取值（必填，业务侧按此值过滤 / 落库） */
    @Schema(description = "字典项实际取值")
    private String dictValue;

    /** 排序号（可选，缺省 0；升序） */
    @Schema(description = "排序号")
    private Integer sort;

    /** 状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;
}
