package com.solo.request;

import lombok.Data;

/**
 * 字典数据分页查询请求参数
 */
@Data
public class DictDataPageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;
    private String dictType;
    private String dictLabel;
    private Integer status;
}
