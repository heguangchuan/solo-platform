package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.response.GbChannelStatusCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
 * 单 SQL 多行，不依赖预 SELECT。10w+ 通道 Catalog 同步落库是核心性能路径，必须用单 SQL
 * 批量方案——逐行 + {@code <if>} 动态 SET 在 4 万通道场景下要走 4 万次网络往返（4-20 秒），
 * 不可接受。</p>
 *
 * <p>条件更新用 SQL 端的 {@code COALESCE(VALUES(x), x)} 实现「设备没报就保留现有值」——行为
 * 等价于 MyBatis {@code <if>}，但放在单 SQL 内执行，4 万行一次往返搞定（10-50ms）。</p>
 */
@Mapper
public interface GbDeviceChannelMapper extends BaseMapper<GbDeviceChannel> {

    /**
     * 批量 upsert（INSERT ... ON DUPLICATE KEY UPDATE），走业务唯一键 uk_device_channel。
     * <p>单 SQL 内插多行（行数由调用方控制，&lt;= 1000），避免 N+1 写。
     * 适用于 10w+ 通道的 Catalog 同步场景：把成百上千条 channel 一次性写库，4 万通道实测
     * 单 SQL 10-50ms 完成。</p>
     *
     * <p>条件更新（{@code COALESCE(VALUES(x), x)}）：设备 XML 里有值的字段用设备值，没报
     * （XML 里 {@code <xxx/>} 为空）的字段保留数据库现有值——防止设备重报时把人工设
     * 的字段（经纬度、地址、行政区划、厂商、IP、端口等大部分 IPC 不在 Catalog XML 里报
     * 的）清空。</p>
     *
     * <p>保留语义（不在 ON DUPLICATE KEY UPDATE 中覆盖）：
     *   <ul>
     *     <li>{@code parental} — 通道/分组类型，由系统维护</li>
     *     <li>{@code parent_id} — 父项国标码，人工编排</li>
     *     <li>{@code source} — 数据来源（系统/导入/注册/级联）</li>
     *     <li>{@code create_time} — MyBatis-Plus FieldFill.INSERT 自动维护</li>
     *   </ul>
     * </p>
     *
     * @param channels 待写入的 catalog item 列表（调用方需预填 id）
     * @return 受影响行数（MySQL 约定：1=INSERT, 2=UPDATE, 0=值未变）
     */
    int batchUpsert(@Param("list") List<GbDeviceChannel> channels);

    /**
     * 按 parental（通道/分组）+ status 分组统计数量（全局口径，与列表查询条件无关）。
     * 分组（parental=1）status 固定为 "ON"，与在线通道同值，故必须带 parental 区分。
     */
    @Select("SELECT parental, status, COUNT(*) AS count FROM gb_device_channel GROUP BY parental, status")
    List<GbChannelStatusCountVO> selectStatusCount();
}
