package com.solo.system.request;

import lombok.Data;

import java.util.List;

/**
 * 权限导出请求
 *
 * <p>对应接口 {@code POST /api/permission/export}。
 * {@code ids} 非空时按 ID 集合导出，缺省按下方筛选条件全量导出。</p>
 */
@Data
public class PermissionExportRequest {

    /** 指定导出的权限 ID 集合（可选；空则按下方筛选条件全量导出） */
    private List<Long> ids;

    /** 按权限名称模糊（可选） */
    private String permissionName;

    /** 按权限编码模糊（可选） */
    private String permissionCode;

    /** 按类型过滤（可选）：{@code 1=菜单}、{@code 2=按钮} */
    private Integer type;

    /** 按状态过滤（可选）：{@code 0=禁用}、{@code 1=启用} */
    private Integer status;
}
