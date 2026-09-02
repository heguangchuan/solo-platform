package com.solo.system.request;

import lombok.Data;

/**
 * 参数新增/编辑请求体
 *
 * <p>对应接口 {@code POST /api/config}、{@code PUT /api/config/{id}}。
 * 业务通过 {@code configKey} 在运行期读取 {@code configValue}。</p>
 */
@Data
public class ConfigRequest {

    /** 参数名称（必填，管理用） */
    private String configName;

    /** 参数编码（必填，全局唯一；业务侧按此 key 读取） */
    private String configKey;

    /** 参数取值（必填） */
    private String configValue;

    /** 备注（可选） */
    private String remark;
}
