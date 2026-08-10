package com.solo.gb28181.mapper;

import com.solo.gb28181.response.CatalogItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 资源目录项统一读模型 Mapper。
 *
 * <p>不继承 {@code BaseMapper}——它不对应单张表，而是把 {@code gb_group} 与 {@code gb_device_channel}
 * UNION 成 {@link CatalogItem} 统一形态。{@code parent_code} 取父级 20 位国标码：分组 LEFT JOIN 自身按
 * parent_id 反查父分组 group_id；通道 LEFT JOIN gb_group 按 group_id 反查所属分组 group_id。</p>
 *
 * <p><b>id=0 守卫</b>：默认分组主键固定为 0，与「根节点 parent_id=0」的哨兵值撞车。分组自连接加
 * {@code g.parent_id <> 0}，使根分组的 parent_code 得到 null，而非误取到 id=0 默认分组的 code。
 * 通道 group_id=0（挂默认分组）join 到默认分组是正确的，不加此守卫。</p>
 */
@Mapper
public interface CatalogItemMapper {

    @Select("""
            SELECT g.id                                    AS id,
                   g.name                                  AS name,
                   g.group_id                              AS code,
                   p.group_id                              AS parentCode,
                   'group'                                 AS type
              FROM gb_group g
              LEFT JOIN gb_group p ON p.id = g.parent_id AND g.parent_id <> 0
            UNION ALL
            SELECT c.id                                    AS id,
                   COALESCE(NULLIF(c.name, ''), c.report_name) AS name,
                   c.channel_id                            AS code,
                   grp.group_id                            AS parentCode,
                   'channel'                               AS type
              FROM gb_device_channel c
              LEFT JOIN gb_group grp ON grp.id = c.group_id
            """)
    List<CatalogItem> selectAll();
}
