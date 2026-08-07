package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 国标设备通道 Mapper
 * <p>批量 upsert 走 {@code ServiceImpl#saveOrUpdateBatch}（MP 自带：SELECT PK 后批量 INSERT/UPDATE），
 * 不在本接口新增手写方法——避免自造 XML 轮子。</p>
 */
@Mapper
public interface GbDeviceChannelMapper extends BaseMapper<GbDeviceChannel> {
}
