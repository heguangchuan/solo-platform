package com.solo.gb28181.response;

import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 资源目录项统一读模型：把「资源目录分组（{@code gb_group}）」与「通道（{@code gb_device_channel}）」
 * 归一成同一形态，便于共享树 / 级联目录等场景统一读取。
 *
 * <p>等价于视图 {@code view_catalog_channel}，但 {@link #parentCode} 取的是<b>父级 20 位国标码</b>
 * （非雪花 id）——分组沿 parent_id 反查父分组 group_id，通道按 group_id 反查所属分组 group_id。</p>
 *
 * <p>由 {@link com.solo.gb28181.mapper.CatalogItemMapper} 的 UNION 查询产出，只读。</p>
 */
@Data
public class CatalogItem {

    /** 资源主键（雪花）：group→gb_group.id / channel→gb_device_channel.id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 显示名称：分组 name / 通道 name（空则 report_name） */
    private String name;

    /** 本节点 20 位国标码：group→group_id / channel→channel_id */
    private String code;

    /** 父级 20 位国标码：group→父分组 group_id / channel→所属分组 group_id；根节点为 null */
    private String parentCode;

    /** 所属平台ID：0000=自有平台；其他=上级级联来源平台ID */
    private String platformId;

    /** 资源类型：group（资源目录分组）/ channel（通道） */
    private String type;
}
