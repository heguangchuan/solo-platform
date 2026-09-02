package com.solo.gb28181.request;

import lombok.Data;

/**
 * 国标设备分页查询请求
 *
 * <p>对应 {@code POST /api/gb28181/device/page}。分页参数：{@code page} 从 1 开始，默认 1；{@code pageSize} 默认 20。
 * 响应结构为 {@code { total, rows }}。</p>
 */
@Data
public class GbDevicePageRequest {

    /** 当前页码（从 1 开始）；默认 1 */
    private Long page = 1L;

    /** 每页条数；默认 20 */
    private Long pageSize = 20L;

    /**
     * 设备国标 ID（模糊匹配，可选）
     */
    private String deviceId;

    /**
     * 设备名称（模糊匹配，可选）
     */
    private String name;

    /**
     * 在线状态（可选）：{@code 0} 离线、{@code 1} 在线、{@code 2} 未注册
     */
    private Integer status;

    /**
     * 数据来源（可选）：{@code 1} 系统新增、{@code 2} 表格导入、{@code 3} 设备注册
     */
    private Integer source;
}
