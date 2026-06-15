package com.solo.request;

import lombok.Data;

/**
 * 字典类型分页查询请求参数
 */
@Data
public class DictTypePageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;
    private String dictName;
    private String dictType;
    private Integer status;
}
