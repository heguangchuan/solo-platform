package com.solo.system.request;

import lombok.Data;

/**
 * 字典类型新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/dict/type}、{@code PUT /api/dict/type/{id}}。</p>
 */
@Data
public class DictTypeRequest {

    /** 字典类型名称（必填，管理用） */
    private String dictName;

    /** 字典类型编码（必填，全局唯一；业务侧按此编码引用） */
    private String dictType;

    /** 状态（可选）：{@code 0=禁用}、{@code 1=启用} */
    private Integer status;
}
