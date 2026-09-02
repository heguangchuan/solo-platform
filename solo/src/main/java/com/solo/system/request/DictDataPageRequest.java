package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典数据分页查询请求参数
 *
 * <p>分页参数：{@code page} 从 1 开始，{@code pageSize} 默认 20。
 * 对应接口 {@code POST /api/dict/data/page}。</p>
 */
@Schema(description = "字典数据分页查询请求参数")
@Data
public class DictDataPageRequest {

    /** 当前页码（从 1 开始），缺省 1 */
    @Schema(description = "当前页码")
    private Long page = 1L;

    /** 每页条数，缺省 20 */
    @Schema(description = "每页条数")
    private Long pageSize = 20L;

    /** 按字典类型编码过滤（可选） */
    @Schema(description = "字典类型编码")
    private String dictType;

    /** 按字典项标签精确匹配（可选） */
    @Schema(description = "字典项标签")
    private String dictLabel;

    /** 状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    @Schema(description = "状态（0/1）")
    private Integer status;
}
