package com.solo.gb28181.request;

import lombok.Data;

/**
 * 国标资源目录项分页查询请求（通道 + 分组合一）
 */
@Data
public class GbDeviceChannelPageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /** 所属设备国标ID（精确） */
    private String deviceId;

    /** 20 位国标码（模糊） */
    private String channelId;

    /** 名称（模糊） */
    private String name;

    /** 在线状态 ON / OFF（仅通道有效） */
    private String status;

    /** 父分组 GB 码前缀（后端 SQL LIKE 'value%' 匹配） */
    private String parentId;

    /** 资源类型过滤：0=通道 / 1=分组；null=全部（通道 + 分组） */
    private Integer parental;

    /** 数据来源：1 系统新增，2 表格导入，3 设备注册 */
    private Integer source;
}
