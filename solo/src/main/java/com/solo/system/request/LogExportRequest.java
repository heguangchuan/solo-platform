package com.solo.system.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class LogExportRequest extends LogPageRequest {
    private List<Long> ids;
}
