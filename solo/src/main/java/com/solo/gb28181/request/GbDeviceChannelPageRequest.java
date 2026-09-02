package com.solo.gb28181.request;

import lombok.Data;

/**
 * 国标资源目录项分页查询请求（通道 + 分组合一）
 *
 * <p>对应 {@code POST /api/gb28181/device-channel/page}。
 * 通道 / 分组同表，由 {@link #parental} 区分：
 * {@code null}=全部、{@code 0}=仅通道、{@code 1}=仅分组。</p>
 */
@Data
public class GbDeviceChannelPageRequest {

    /** 当前页码（从 1 开始）；默认 1 */
    private Long page = 1L;

    /** 每页条数；默认 20 */
    private Long pageSize = 20L;

    /** 所属设备国标 ID（精确匹配，可选） */
    private String deviceId;

    /** 20 位国标通道码（模糊匹配，可选） */
    private String channelId;

    /** 名称（模糊匹配，可选） */
    private String name;

    /** 在线状态：{@code ON} / {@code OFF} / {@code UNREGISTERED}（仅通道有效，可选） */
    private String status;

    /** 父分组 GB 码前缀（按前缀匹配，可选） */
    private String parentId;

    /** 资源类型过滤：{@code 0}=仅通道、{@code 1}=仅分组、{@code null}=全部（通道 + 分组） */
    private Integer parental;

    /** 数据来源：{@code 1} 系统新增、{@code 2} 表格导入、{@code 3} 设备注册（可选） */
    private Integer source;
}
