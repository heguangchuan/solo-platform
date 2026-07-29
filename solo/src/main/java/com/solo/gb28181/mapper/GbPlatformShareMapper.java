package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.entity.GbPlatformShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 上级平台共享资源 Mapper（gb_platform_share，目录 + 通道合一）。
 */
@Mapper
public interface GbPlatformShareMapper extends BaseMapper<GbPlatformShare> {

    /**
     * 查询某上级平台已勾选共享的所有分组明细。
     * <p>join {@code gb_group}（type='group'），用于向上级回 Catalog Response 时构造分组 Item。</p>
     */
    @Select("SELECT g.* FROM gb_group g " +
            "INNER JOIN gb_platform_share s ON s.resource_id = g.id AND s.type = 'group' " +
            "WHERE s.platform_id = #{platformId} " +
            "ORDER BY g.sort ASC, g.id ASC")
    List<GbGroup> selectSharedGroups(@Param("platformId") Long platformId);

    /**
     * 查询某上级平台已勾选共享的所有通道明细。
     * <p>join {@code gb_device_channel}（type='channel'），用于向上级回 Catalog Response 时构造通道 Item。
     * 一并带出 {@code gb_platform_share.exposed_code}（对上级暴露的唯一码）到瞬态字段
     * {@code GbDeviceChannel.exposedCode}，供上报 / 点播反查用。</p>
     */
    @Select("SELECT ch.*, s.exposed_code AS exposed_code FROM gb_device_channel ch " +
            "INNER JOIN gb_platform_share s ON s.resource_id = ch.id AND s.type = 'channel' " +
            "WHERE s.platform_id = #{platformId} " +
            "ORDER BY ch.channel_id ASC")
    List<GbDeviceChannel> selectSharedChannels(@Param("platformId") Long platformId);

    /**
     * 取某通道（resource_id, type=channel）已分配的 exposed_code（任一平台行，全局复用同一码）。
     * <p>同一通道共享给多个上级时 exposed_code 恒定，故取任意一行即可；无则返回 null。</p>
     */
    @Select("SELECT exposed_code FROM gb_platform_share " +
            "WHERE type = 'channel' AND resource_id = #{resourceId} AND exposed_code IS NOT NULL " +
            "LIMIT 1")
    String findExposedCodeByResourceId(@Param("resourceId") Long resourceId);

    /**
     * 列出全表已用的 channel exposed_code，供生成器扫描最大序号避免撞码。
     */
    @Select("SELECT DISTINCT exposed_code FROM gb_platform_share " +
            "WHERE type = 'channel' AND exposed_code IS NOT NULL")
    List<String> listExposedCodes();
}
