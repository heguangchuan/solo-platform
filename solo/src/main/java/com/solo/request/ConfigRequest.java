package com.solo.request;

import lombok.Data;

/**
 * 参数新增/编辑请求体
 */
@Data
public class ConfigRequest {
    private String configName;
    private String configKey;
    private String configValue;
    private String remark;
}
