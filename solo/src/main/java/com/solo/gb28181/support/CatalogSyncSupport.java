package com.solo.gb28181.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.constant.GbSource;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.service.GbDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>由 {@link com.solo.gb28181.processor.request.message.response.CatalogDataCache}
 * 在阈值/收尾时调 {@link #executeFlush} 提交一次 flush 任务。任务在
 * {@code catalogSyncExecutor} 上跑，DB 写不占用 SIP 消息分发线程。</p>
 *
 * <h3>合并后的数据流</h3>
 * <p>分组（parental=1）与通道（parental=0）落到同一张 {@code gb_device_channel}，靠 parental 区分。
 * item 的 {@code parent_id} 直接是父 item 的 20 位国标码（与父行 {@code channel_id} 对齐），
 * 字符串类型，{@code "0"} 表根（无父）。本类不再做 GB 码 ↔ 雪花 id 转换。</p>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>中间 flush：直接调 {@code channelService.saveOrUpdateBatch(items)}，单 SQL upsert。</li>
 *   <li>final flush：额外做两件事——
 *     <ul>
 *       <li><b>孤儿 parentId 回落</b>：item 的 {@code parent_id}（GB 码）在库/本批都找不到 →
 *           改挂 {@code device.getDeviceId()}，让目录树有唯一收口（中间 flush 不做，避免"先报子后报父"误伤）</li>
 *       <li>若 {@code completenessRatio >= threshold} 清扫"已不存在通道"（仅 parental=0 行），
 *           否则保留以备下次 sync 补齐</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>依赖说明</h3>
 * <p>本类直接走 {@link GbDeviceMapper} / {@link GbDeviceChannelMapper}，单表 CRUD 包装不绕 service。
 * 仍保留 {@link GbDeviceService#evictProfileCache}（设备档案缓存失效，业务方法，跨 Redis + DB）。</p>
 */
@Slf4j
@Service
public class CatalogSyncSupport {

    /** 根哨兵——parent_id == "0" 表无父 */
    private static final String ROOT_PARENT = "0";

    private final Gb28181Properties properties;
    private final GbDeviceService deviceService;
    private final GbDeviceChannelMapper channelMapper;
    private final GbDeviceMapper deviceMapper;
    private final ThreadPoolTaskExecutor executor;

    /**
     * 显式构造器——Lombok @RequiredArgsConstructor 不会把字段上的 @Qualifier 复制到构造器参数上，
     * 这里手写保证 executor 按 bean 名称注入。
     */
    public CatalogSyncSupport(Gb28181Properties properties,
                              GbDeviceService deviceService,
                              GbDeviceChannelMapper channelMapper,
                              GbDeviceMapper deviceMapper,
                              @Qualifier("catalogSyncExecutor") ThreadPoolTaskExecutor executor) {
        this.properties = properties;
        this.deviceService = deviceService;
        this.channelMapper = channelMapper;
        this.deviceMapper = deviceMapper;
        this.executor = executor;
    }

    /**
     * 提交一次 flush 任务到 catalogSyncExecutor。
     */
    public void executeFlush(CatalogSyncContext ctx, List<GbDeviceChannel> items, boolean isFinalFlush) {
        if (ctx == null) {
            return;
        }
        // 队列前再 check 一次：cancel 已发生（done=true）→ 不入队
        // 但 final flush 必须入队——finalize 已设 done=true，这里再 check 会自误，
        //   导致 sumNum 收齐时 final flush 被自己的 done=true 拦截、Item 永远不落库。
        //   intermediate flush（accumulate 触发的）依然走 done check 尊重 cancel。
        if (!isFinalFlush && ctx.done) {
            log.info("[通道目录] flush 被取消（入队前） deviceId={} sn={} items={}",
                    ctx.deviceId, ctx.sn, items.size());
            return;
        }
        executor.execute(() -> {
            // 队列中、运行前的窗口：cancel 可能在这一刻发生，再 check 一次
            // 同上：final flush 不受 done 拦截
            if (!isFinalFlush && ctx.done) {
                log.info("[通道目录] 分片落库取消 设备:{} SN:{} 原因:运行前", ctx.deviceId, ctx.sn);
                return;
            }
            long start = System.currentTimeMillis();
            try {
                doFlush(ctx, items, isFinalFlush);
                long cost = System.currentTimeMillis() - start;
                log.info("[通道目录] 分片落库完成 设备:{} SN:{} 条目:{} 是否终片:{} 耗时:{}ms 累计分片:{} 累计条目:{}",
                        ctx.deviceId, ctx.sn, items.size(), isFinalFlush, cost,
                        ctx.chunkCount.get(), ctx.totalReceived.get());
            } catch (Exception e) {
                log.error("[通道目录] 分片落库失败 设备:{} SN:{} 是否终片:{} 错误:{}",
                        ctx.deviceId, ctx.sn, isFinalFlush, e.getMessage(), e);
            }
        });
    }

    private void doFlush(CatalogSyncContext ctx, List<GbDeviceChannel> items, boolean isFinalFlush) {
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, ctx.deviceId));
        if (device == null) {
            log.warn("[通道目录] flush 跳过：设备不存在 deviceId={}", ctx.deviceId);
            return;
        }

        // 入库前预处理
        if (!items.isEmpty()) {
            // 预填 id（雪花）—— batchUpsert 走业务唯一键 uk_device_channel (device_id, channel_id)
            // 同时覆盖通道和分组（device_id 标识推送源设备）。
            for (GbDeviceChannel it : items) {
                if (it.getId() == null) {
                    it.setId(IdWorker.getId());
                }
                // 源统一 REGISTER（合并后不再区分级联，ADR-016 决策点）
                it.setSource(GbSource.REGISTER);
            }

            // final flush 阶段做"孤儿 parentId 回落"：parent_id（GB 码）在库里/本批里都找不到
            // （设备漏报父节点、报了非法 parent_id），一律改挂到 device.deviceId 下——
            // 让目录树有唯一收口，不出现游离顶层节点。中间 flush 不做：设备可能先报子、后报父，那时判定会误伤。
            if (isFinalFlush) {
                Set<String> knownCodes = new HashSet<>();
                // 本批已见过的 channel_id 算"父可达"
                for (GbDeviceChannel it : items) {
                    if (it.getChannelId() != null) knownCodes.add(it.getChannelId());
                }
                // 库里已存在的 channel_id 也算"父可达"（本次没重报但历史已入库）
                channelMapper.selectList(
                                new LambdaQueryWrapper<GbDeviceChannel>().select(GbDeviceChannel::getChannelId))
                        .forEach(e -> { if (e.getChannelId() != null) knownCodes.add(e.getChannelId()); });

                int reparented = 0;
                for (GbDeviceChannel it : items) {
                    String pid = it.getParentId();
                    if (ROOT_PARENT.equals(pid) || (pid != null && knownCodes.contains(pid))) {
                        continue;
                    }
                    it.setParentId(device.getDeviceId());
                    reparented++;
                }
                if (reparented > 0) {
                    log.info("[通道目录] 孤儿 parentId 回落到设备节点 deviceId={} count={}/{}",
                            device.getDeviceId(), reparented, items.size());
                }
            }

            channelMapper.batchUpsert(items);
        }

        // final flush 阶段：依据 completenessRatio 决定是否清理"已不存在通道"
        if (isFinalFlush) {
            double ratio = ctx.completenessRatio;
            double threshold = properties.getCatalog().getCompletenessThreshold();
            Long countAfter = channelMapper.selectCount(
                    new LambdaQueryWrapper<GbDeviceChannel>()
                            .eq(GbDeviceChannel::getDeviceId, device.getDeviceId())
                            .eq(GbDeviceChannel::getParental, 0));
            if (ratio >= threshold) {
                int rows = channelMapper.delete(
                        new LambdaQueryWrapper<GbDeviceChannel>()
                                .eq(GbDeviceChannel::getDeviceId, device.getDeviceId())
                                .eq(GbDeviceChannel::getParental, 0)
                                .notIn(GbDeviceChannel::getChannelId, ctx.syncedItemIds));
                log.info("[通道目录] final flush 清扫完成 deviceId={} ratio={} threshold={} 本轮唯一={} 删除失效通道={}",
                        ctx.deviceId, String.format("%.4f", ratio), threshold,
                        ctx.syncedItemIds.size(), rows);
            } else {
                log.warn("[通道目录] final flush 保留残缺数据 deviceId={} ratio={} < threshold={} 保留 {} 条已入库通道，等下次 sync 补齐",
                        ctx.deviceId, String.format("%.4f", ratio), threshold, countAfter);
            }
            // 落库后回写 lastCatalogSyncTime（只更这一个字段，避免覆盖并发写入的其他字段）
            deviceMapper.update(null,
                    new LambdaUpdateWrapper<GbDevice>()
                            .eq(GbDevice::getId, device.getId())
                            .set(GbDevice::getLastCatalogSyncTime, LocalDateTime.now()));
            // 失效档案缓存：lastCatalogSyncTime 变了，下条 MESSAGE 应拿到最新值
            deviceService.evictProfileCache(device.getDeviceId());
        }
    }
}
