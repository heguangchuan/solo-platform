package com.solo.system.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 参数导出请求
 *
 * <p>继承 {@link ConfigPageRequest} 复用筛选条件；{@code ids} 非空时按 ID 集合导出，
 * 缺省按父类筛选条件全量导出。对应接口 {@code POST /api/config/export}。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ConfigExportRequest extends ConfigPageRequest {

    /** 指定导出的参数 ID 集合（可选；空则按父类筛选条件全量导出） */
    private List<Long> ids;
}
