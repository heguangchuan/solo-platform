package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.response.GbChannelStatusCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>本表业务键 ≠ 主键：主键是雪花 {@code id}，业务唯一键是 {@code uk_device_channel(device_id, channel_id)}。
 * 合并后无 {@code device_db_id} 字段（去除冗余雪花主键），关系仅由 GB 码 {@code device_id} 维系。
 * MyBatis-Plus 的 {@code saveOrUpdateBatch} 按主键 id 判定存在性，会走 INSERT 然后撞业务唯一键
 * （{@code Duplicate entry ... for key 'uk_device_channel'}），不适合本表场景。</p>
 *
 * <p>所以这里必须自造轮子：用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 走业务唯一键 upsert，
 * 单 SQL 多行，不依赖预 SELECT。10w+ 通道 Catalog 同步落库是核心性能路径，必须用单 SQL
 * 批量方案——逐行 + {@code <if>} 动态 SET 在 4 万通道场景下要走 4 万次网络往返（4-20 秒），
 * 不可接受。</p>
 *
 * <p>条件更新用 SQL 端的 {@code COALESCE(VALUES(x), x)} 实现「设备没报就保留现有值」——行为
 * 等价于 MyBatis {@code <if>}，但放在单 SQL 内执行，4 万行一次往返搞定（10-50ms）。</p>
 */
@Mapper
public interface GbDeviceChannelMapper extends BaseMapper<GbDeviceChannel> {

    int batchUpsert(@Param("list") List<GbDeviceChannel> channels);

    @Select("SELECT parental, status, COUNT(*) AS count FROM gb_device_channel GROUP BY parental, status")
    List<GbChannelStatusCountVO> selectStatusCount();
}
