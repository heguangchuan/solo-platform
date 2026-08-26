package com.solo.gb28181.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.response.MapMarker;
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
     * 首页地图打点：按经纬度矩形范围 + 必带坐标 + 限定 5000 条，返回单字母字段的紧凑 marker 列表。
     * <p>配合 {@link com.solo.gb28181.service.GbDeviceChannelService#queryMapMarkers} 使用，
     * 上层负责 bounds 校验、计数等业务控制，本方法只做单条 SQL 查询。</p>
     *
     * <p>{@code target} 是瓦片系（与 application.yaml 的 {@code map.coord-system} 联动）：
     *   <ul>
     *     <li>{@code GCJ02} / {@code BD09} → 选 longitude_gcj02 / latitude_gcj02 副本列</li>
     *     <li>{@code WGS84} / 其他 → 选 longitude / latitude 主列</li>
     *   </ul>
     * DB 已经冗余存了 3 套坐标系版本，查询时按 target 直接 SELECT 对应列，零转换。</p>
     *
     * @param minLng 视口西边经度（含，目标系）
     * @param minLat 视口南边纬度（含，目标系）
     * @param maxLng 视口东边经度（含，目标系）
     * @param maxLat 视口北边纬度（含，目标系）
     * @param target 瓦片坐标系（WGS84 / GCJ02 / BD09）
     * @return 紧凑字段的 marker 列表（最多 5000 条）
     */
    List<MapMarker> selectForMap(@Param("minLng") double minLng,
                                 @Param("minLat") double minLat,
                                 @Param("maxLng") double maxLng,
                                 @Param("maxLat") double maxLat,
                                 @Param("target") String target);
}
