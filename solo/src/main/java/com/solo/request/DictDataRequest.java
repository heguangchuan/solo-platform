package com.solo.request;

import lombok.Data;

/**
 * 字典数据新增/编辑请求体
 */
@Data
public class DictDataRequest {
    private String dictType;
    private String dictLabel;
    private String dictValue;
    private Integer sort;
    private String cssClass;
    private Integer status;
    private String remark;
}
