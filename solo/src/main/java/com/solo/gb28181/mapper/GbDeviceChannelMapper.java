package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 国标设备通道 Mapper
 *
 * <p>本表业务键 ≠ 主键：主键是雪花 {@code id}，业务唯一键是 {@code uk_device_channel(device_id, channel_id)}。
 * 合并后无 {@code device_db_id} 字段（去除冗余雪花主键），关系仅由 GB 码 {@code device_id} 维系。
 * MyBatis-Plus 的 {@code saveOrUpdateBatch} 按主键 id 判定存在性，会走 INSERT 然后撞业务唯一键
 * （{@code Duplicate entry ... for key 'uk_device_channel'}），不适合本表场景。</p>
 *
 * <p>所以这里必须自造轮子：用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 走业务唯一键 upsert，
 * 单 SQL 多行，不依赖预 SELECT。10w+ 通道 Catalog 同步落库是核心性能路径，不能用 MP 默认的
 * "SELECT all + 拆 + 批量 INSERT/UPDATE" 方案（多一次 SELECT + 业务键不对）。</p>
 */
@Mapper
public interface GbDeviceChannelMapper extends BaseMapper<GbDeviceChannel> {

    /**
     * 批量 upsert（INSERT ... ON DUPLICATE KEY UPDATE），走业务唯一键 uk_device_channel。
     * <p>单 SQL 内插多行（行数由调用方控制，&lt;= 1000），避免 N+1 写。
     * 适用于 10w+ 通道的 Catalog 同步场景：把成百上千条 channel 一次性写库。</p>
     * <p>保留语义：{@code parent_id}（父项国标码，人工编排）、{@code source}（数据来源）、{@code create_time}
     * 不在 ON DUPLICATE KEY UPDATE 中覆盖——已存在的 channel 保留原值；其余业务字段以本次上报值为准。
     * {@code id} 走雪花算法预分配，调用方需在调用前 setId（也用于支持后续按 id 引用）。</p>
     *
     * @param channels 待写入的 catalog item 列表（调用方需预填 id）
     * @return 受影响行数（MySQL 约定：1=INSERT, 2=UPDATE, 0=值未变）
     */
    int batchUpsert(@Param("list") List<GbDeviceChannel> channels);
}
