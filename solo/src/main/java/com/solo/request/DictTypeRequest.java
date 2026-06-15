package com.solo.request;

import lombok.Data;

/**
 * 字典类型新增/编辑请求体
 */
@Data
public class DictTypeRequest {
    private String dictName;
    private String dictType;
    private Integer status;
    private String remark;
}
