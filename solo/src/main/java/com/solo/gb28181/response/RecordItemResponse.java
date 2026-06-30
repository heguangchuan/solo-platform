package com.solo.gb28181.response;

import lombok.Data;

/**
 * RecordInfo 单条录像段（设备 RecordInfo 响应 {@code <Item>} 节点的 POJO 表示）
 *
 * <p>字段对齐 GB/T 28181-2016 9.5.5 RecordInfo Item：</p>
 * <ul>
 *   <li>{@code startTime / endTime} — 设备原始 ISO-8601 字符串（如 {@code 2026-06-30T08:00:00}）</li>
 *   <li>{@code type} — {@code time} 计划 / {@code alarm} 报警 / {@code manual} 手动</li>
 *   <li>{@code fileSize} — 字节，可能为 0（设备未提供）</li>
 *   <li>{@code secrecy} — 保密属性，0 不限</li>
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

    /** 起始时间 ISO-8601 */
    private String startTime;

    /** 结束时间 ISO-8601 */
    private String endTime;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 录像类型：time / alarm / manual */
    private String type;

    /** 录像设备 ID（NVR 等录像聚合设备） */
    private String recorderId;

    /** 保密属性：0 不限 1 涉密 */
    private Integer secrecy;
}
