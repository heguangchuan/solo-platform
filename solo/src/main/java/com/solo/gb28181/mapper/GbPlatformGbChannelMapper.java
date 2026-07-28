package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPlatformGbChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 上级平台共享通道 Mapper
 */
@Mapper
public interface GbPlatformGbChannelMapper extends BaseMapper<GbPlatformGbChannel> {

    /**
     * 查询某上级平台已勾选共享的所有通道明细。
     * <p>join {@code gb_device_channel}，用于向上级回 Catalog Response 时构造通道 Item。</p>
     */
    @Select("SELECT ch.* FROM gb_device_channel ch " +
            "INNER JOIN gb_platform_gb_channel s ON s.channel_id = ch.id " +
            "WHERE s.platform_id = #{platformId} " +
            "ORDER BY ch.channel_id ASC")
    List<GbDeviceChannel> selectSharedChannels(@Param("platformId") Long platformId);
}
