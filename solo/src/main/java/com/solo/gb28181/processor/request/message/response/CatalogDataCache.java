package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalog 分片缓存
 * <p>设备的 Catalog 回复会按 SumNum 分多个 MESSAGE 报文分批送回，必须先在内存缓存累计，
 * 收齐 SumNum 条后一次性 upsert，避免半状态目录入库。</p>
 * <p>Item 类型分两类：通道（普通前端设备 130-199）与 目录节点（215 业务分组 / 216 虚拟组织）；
 * 两者共同计入 SumNum，分别累计到 {@link Batch#channels} 与 {@link Batch#groups}。</p>
 * <p>按 (deviceId, sn) 作为 batch key，超时 30 秒未收齐自动丢弃（防止设备发了一半掉线导致内存泄漏）。</p>
 */
@Slf4j
@Component
public class CatalogDataCache {

    /** 60 秒以上未更新的 batch 视为僵死，定期清理 */
    private static final long EXPIRE_MS = 60_000L;

    private final Map<String, Batch> cache = new ConcurrentHashMap<>();

    /**
     * 累加一片 Catalog Item。通道与目录节点两个列表分别追加，都计入总数与 sumNum 比较。
     *
     * @param chunkChannels 本片中的通道 Item（可空列表）
     * @param chunkGroups   本片中的目录节点 Item（可空列表）
     * @param channelParentIdMap 本片通道的 <ParentID> 映射（channelId → parentId），用于后续目录归属
     * @return 达到 SumNum 时返回完整 batch；否则返回 null
     */
    public Batch accumulate(String deviceId, int sn, int sumNum,
                            List<GbDeviceChannel> chunkChannels,
                            List<GbGroup> chunkGroups,
                            Map<String, String> channelParentIdMap) {
        String key = key(deviceId, sn);
        Batch batch = cache.computeIfAbsent(key, k -> new Batch(deviceId, sn, sumNum));
        synchronized (batch) {
            if (chunkChannels != null && !chunkChannels.isEmpty()) batch.channels.addAll(chunkChannels);
            if (chunkGroups != null && !chunkGroups.isEmpty()) batch.groups.addAll(chunkGroups);
            if (channelParentIdMap != null && !channelParentIdMap.isEmpty()) {
                batch.channelParentIdMap.putAll(channelParentIdMap);
            }
            batch.lastUpdateMs = System.currentTimeMillis();
            int total = batch.channels.size() + batch.groups.size();
            log.info("[通道目录] 分片累积 deviceId={} sn={} {}/{}（通道 {}，目录 {}）",
                    deviceId, sn, total, sumNum, batch.channels.size(), batch.groups.size());
            if (total >= batch.sumNum) {
                batch.done = true;
                cache.remove(key);
            }
        }
        return batch.done ? batch : null;
    }

    /** 定时清理过期 batch（DeviceOfflineDetector 之类的定时任务里调一下即可） */
    public void sweep() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> {
            if (now - e.getValue().lastUpdateMs > EXPIRE_MS) {
                int total = e.getValue().channels.size() + e.getValue().groups.size();
                log.warn("[通道目录] 分片超时丢弃 key={} 累计={}", e.getKey(), total);
                return true;
            }
            return false;
        });
    }

    private static String key(String deviceId, int sn) {
        return deviceId + ":" + sn;
    }

    /** 一次目录同步的累积状态 */
    public static class Batch {
        public final String deviceId;
        public final int sn;
        public final int sumNum;
        public final List<GbDeviceChannel> channels = new ArrayList<>();
        public final List<GbGroup> groups = new ArrayList<>();
        /** 通道 Item 的 <ParentID> 映射（channelId → parentId），用于批量入库时按目录节点归属 */
        public final Map<String, String> channelParentIdMap = new HashMap<>();
        public volatile long lastUpdateMs = System.currentTimeMillis();
        public volatile boolean done = false;

        public Batch(String deviceId, int sn, int sumNum) {
            this.deviceId = deviceId;
            this.sn = sn;
            this.sumNum = sumNum;
        }
    }
}
