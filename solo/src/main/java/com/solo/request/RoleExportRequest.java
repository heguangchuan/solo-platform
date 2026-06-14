package com.solo.request;

import lombok.Data;

import java.util.List;

@Data
public class RoleExportRequest extends RolePageRequest {
    private List<Long> ids;
}
