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

// parent_id 直接存父节点的国标码，"0" 表示根。
// 孤儿改挂设备节点和清理旧通道都只在 final flush 做；中途可能先收到子节点，别急着修。
@Slf4j
@Service
public class CatalogSyncSupport {

    private static final String ROOT_PARENT = "0";

    private final Gb28181Properties properties;
    private final GbDeviceService deviceService;
    private final GbDeviceChannelMapper channelMapper;
    private final GbDeviceMapper deviceMapper;
    private final ThreadPoolTaskExecutor executor;

    // 手写构造器是因为 Lombok 不会把字段上的 @Qualifier 搬到构造参数上。
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

    public void executeFlush(CatalogSyncContext ctx, List<GbDeviceChannel> items, boolean isFinalFlush) {
        if (ctx == null) {
            return;
        }
        // final flush 前 finalize 已经把 done 设成 true，不能再拿 done 拦它，不然最后一批永远落不了库
        if (!isFinalFlush && ctx.done) {
            log.info("[通道目录] flush 被取消（入队前） deviceId={} sn={} items={}",
                    ctx.deviceId, ctx.sn, items.size());
            return;
        }
        executor.execute(() -> {
            // 排队期间也可能被取消，普通 flush 跑之前再查一次
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

        if (!items.isEmpty()) {
            for (GbDeviceChannel it : items) {
                if (it.getId() == null) {
                    it.setId(IdWorker.getId());
                }
                it.setSource(GbSource.REGISTER);
            }

            // 最后一批才修孤儿 parentId；中途可能父节点还没来，修早了就误挂
            if (isFinalFlush) {
                Set<String> knownCodes = new HashSet<>();
                for (GbDeviceChannel it : items) {
                    if (it.getChannelId() != null) knownCodes.add(it.getChannelId());
                }
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
