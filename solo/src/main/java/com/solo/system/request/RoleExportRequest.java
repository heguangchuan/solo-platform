package com.solo.system.request;

import lombok.Data;

import java.util.List;

/**
 * 角色导出请求
 *
 * <p>继承 {@link RolePageRequest} 复用筛选条件；{@code ids} 非空时按 ID 集合导出，
 * 缺省按父类筛选条件全量导出。对应接口 {@code POST /api/role/export}。</p>
 */
@Data
public class RoleExportRequest extends RolePageRequest {

    /** 指定导出的角色 ID 集合（可选；空则按父类筛选条件全量导出） */
    private List<Long> ids;
}
