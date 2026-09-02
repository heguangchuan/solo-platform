package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典类型分页查询请求参数
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
 * 对应接口 {@code POST /api/dict/type/page}。</p>
 */
@Schema(description = "字典类型分页查询请求参数")
@Data
public class DictTypePageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    @Schema(description = "当前页码")
    private Long page = 1L;

    /** 每页条数，缺省 20 */
    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    /** 字典类型名称（可选，精确匹配） */
    @Schema(description = "字典类型名称")
    private String dictName;

    /** 字典类型编码（可选，精确匹配） */
    @Schema(description = "字典类型编码")
    private String dictType;

    /** 状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;
}
