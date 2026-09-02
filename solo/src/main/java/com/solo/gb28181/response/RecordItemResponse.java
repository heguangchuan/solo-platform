package com.solo.gb28181.response;

import lombok.Data;

/**
 * 录像段信息项（设备录像列表的单条记录）
 *
 * <p>对应 {@code POST /api/gb28181/playback/query} 返回数组的元素。</p>
 *
 * <p>字段含义：</p>
 * <ul>
 *   <li>{@link #startTime} / {@link #endTime} — 设备原始 ISO-8601 字符串（如 {@code 2026-06-30T08:00:00}）</li>
 *   <li>{@link #type} — {@code time} 计划 / {@code alarm} 报警 / {@code manual} 手动</li>
 *   <li>{@link #fileSize} — 字节，可能为 0（设备未提供）</li>
 *   <li>{@link #secrecy} — 保密属性，{@code 0} 不限、{@code 1} 涉密</li>
 * </ul>
 */
@Data
public class RecordItemResponse {

    /** 通道国标 ID */
    private String channelId;

    /** 录像文件名（设备给的，未必有意义） */
    private String name;

    /** 录像文件路径（设备本地路径，下载用） */
    private String filePath;

    /** 录像地址 */
    private String address;

    /** 起始时间 ISO-8601（如 {@code 2026-06-30T08:00:00}） */
    private String startTime;

    /** 结束时间 ISO-8601 */
    private String endTime;

    /** 文件大小（字节），设备未提供时为 0 */
    private Long fileSize;

    /** 录像类型：{@code time} 计划 / {@code alarm} 报警 / {@code manual} 手动 */
    private String type;

    /** 录像设备 ID（NVR 等录像聚合设备，可为空） */
    private String recorderId;

    /** 保密属性：{@code 0} 不限、{@code 1} 涉密 */
    private Integer secrecy;
}
