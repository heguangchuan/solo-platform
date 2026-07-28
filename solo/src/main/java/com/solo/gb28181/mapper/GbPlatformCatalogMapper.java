package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.entity.GbPlatformCatalog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 上级平台共享目录 Mapper
 */
@Mapper
public interface GbPlatformCatalogMapper extends BaseMapper<GbPlatformCatalog> {

    /**
     * 查询某上级平台已勾选共享的所有分组明细。
     * <p>join {@code gb_group}，用于向上级回 Catalog Response 时构造分组 Item。</p>
     */
    @Select("SELECT g.* FROM gb_group g " +
            "INNER JOIN gb_platform_catalog c ON c.group_id = g.id " +
            "WHERE c.platform_id = #{platformId} " +
            "ORDER BY g.sort ASC, g.id ASC")
    List<GbGroup> selectSharedGroups(@Param("platformId") Long platformId);
}
