package com.solo.gb28181.request;

import lombok.Data;

/**
 * 国标资源目录分组分页查询请求
 */
@Data
public class GbGroupPageRequest {

    private Long page = 1L;
    private Long pageSize = 20L;

    /** 分组国标ID（模糊） */
    private String groupId;

    /** 分组名称（模糊） */
    private String name;

    /** 父节点主键（按目录层级查询） */
    private String parentId;

    /** 所属平台ID：0000=自有；其他=级联来源 */
    private String platformId;
}
