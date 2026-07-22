package com.solo.system.request;

import lombok.Data;

import java.util.List;

@Data
public class UserExportRequest extends UserPageRequest {
    private List<Long> ids;
}
