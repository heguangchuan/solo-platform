package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPlatformShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 上级平台共享资源 Mapper（gb_platform_share，catalog item 共享，ADR-023）。
 *
 * <p>分组/通道统一存 {@code gb_device_channel}（靠 {@code parental} 区分），
 * 本表只存 {@code (platform_id, resource_id)} 关系；查询时 JOIN 派生 parental。</p>
 */
@Mapper
public interface GbPlatformShareMapper extends BaseMapper<GbPlatformShare> {

    /**
     * 查询某上级平台已勾选共享的所有 catalog item（含分组/通道）。
     * <p>join {@code gb_device_channel}，按 resource_id 一并带出，调用方按 {@code parental}
     * 派生 type；通过 {@code gb_platform_share.exposed_code} 一并带出对上级暴露的唯一码
     * （仅通道有效，分行为 NULL）。</p>
     */
    @Select("SELECT ch.*, s.exposed_code AS exposed_code FROM gb_device_channel ch " +
            "INNER JOIN gb_platform_share s ON s.resource_id = ch.id " +
            "WHERE s.platform_id = #{platformId} " +
            "ORDER BY ch.parental DESC, ch.channel_id ASC")
    List<GbDeviceChannel> selectSharedCatalogItems(@Param("platformId") Long platformId);

    /**
     * 查询某上级平台已勾选共享的所有通道明细。
     * <p>仅返回 {@code parental=0} 的 catalog item（实际通道），分组不会混入。
     * 与 {@link #selectSharedCatalogItems} 区别：本方法只筛通道，省去调用方过滤。</p>
     */
    @Select("SELECT ch.*, s.exposed_code AS exposed_code FROM gb_device_channel ch " +
            "INNER JOIN gb_platform_share s ON s.resource_id = ch.id " +
            "WHERE s.platform_id = #{platformId} AND ch.parental = 0 " +
            "ORDER BY ch.channel_id ASC")
    List<GbDeviceChannel> selectSharedChannels(@Param("platformId") Long platformId);
}
