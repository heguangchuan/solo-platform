package com.solo.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 日志导出请求
 *
 * <p>继承 {@link LogPageRequest} 复用筛选条件；{@code ids} 非空时按 ID 集合导出，
 * 缺省按父类筛选条件全量导出。对应接口 {@code POST /api/log/export}。</p>
 */
@Schema(description = "日志导出请求")
@EqualsAndHashCode(callSuper = true)
@Data
public class LogExportRequest extends LogPageRequest {

    /** 指定导出的日志 ID 集合（可选；空则按父类筛选条件全量导出） */
    @Schema(description = "指定导出的日志 ID 集合")
    private List<Long> ids;
}
