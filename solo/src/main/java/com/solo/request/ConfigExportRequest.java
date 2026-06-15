package com.solo.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConfigExportRequest extends ConfigPageRequest {
    private List<Long> ids;
}
