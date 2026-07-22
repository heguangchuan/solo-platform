package com.solo.system.request;

import lombok.Data;

import java.util.List;

@Data
public class PermissionExportRequest {
    private List<Long> ids;
    private String permissionName;
    private String permissionCode;
    private Integer type;
    private Integer status;
}
