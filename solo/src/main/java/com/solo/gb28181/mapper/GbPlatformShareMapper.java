package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPlatformShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>{@code gb_platform_share} <b>只存通道</b>（{@code gb_device_channel} 中 {@code parental=0} 的行），
 * 本表存 {@code (platform_id, group_id, resource_id)} + 可选撞码重映射 {@code exposed_code}。
 * 复杂查询（JOIN 派生、NOT EXISTS 排除等）写在 {@code mapper/xml/GbPlatformShareMapper.xml}，
 * 简单 CRUD 走 {@link BaseMapper}。</p>
 *
 * <p>2026-08 改造：share 表与 {@code gb_platform_group} 关联，UI 强制先选组才能挂载；
 * 同一通道在同一平台下只能挂载一次（{@code uk_platform_resource}）。弹窗所有"按组"查询走
 * group_id 过滤；catalog 响应 / INVITE 反查仍按 platform_id 全量查（不分组）——上级不关心挂在哪个组里。</p>
 */
@Mapper
public interface GbPlatformShareMapper extends BaseMapper<GbPlatformShare> {

    List<GbDeviceChannel> selectSharedChannels(Long platformId);

    IPage<GbDeviceChannel> selectSharedByGroupPage(Page<GbDeviceChannel> page,
                                                   @Param("platformId") Long platformId,
                                                   @Param("groupId") Long groupId,
                                                   @Param("keyword") String keyword);

    IPage<GbDeviceChannel> selectUnmountedByGroupPage(Page<GbDeviceChannel> page,
                                                      @Param("platformId") Long platformId,
                                                      @Param("groupId") Long groupId,
                                                      @Param("keyword") String keyword);

    List<Long> selectUnmountedByGroupAllIds(@Param("platformId") Long platformId,
                                            @Param("groupId") Long groupId);
}
