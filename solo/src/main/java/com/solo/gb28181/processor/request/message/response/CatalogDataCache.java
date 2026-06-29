package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.entity.GbDeviceChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalog 分片缓存
 * <p>设备的 Catalog 回复会按 SumNum 分多个 MESSAGE 报文分批送回，必须先在内存缓存累计，
 * 收齐 SumNum 条后一次性 upsert，避免半状态目录入库。</p>
 * <p>按 (deviceId, sn) 作为 batch key，超时 30 秒未收齐自动丢弃（防止设备发了一半掉线导致内存泄漏）。</p>
 */
@Slf4j
@Component
public class CatalogDataCache {

    /** 60 秒以上未更新的 batch 视为僵死，定期清理 */
    private static final long EXPIRE_MS = 60_000L;

    private final Map<String, Batch> cache = new ConcurrentHashMap<>();

    /** 累加一批通道，若达到 SumNum 返回完整列表 + 标记 done；否则返回 null */
    public Batch accumulate(String deviceId, int sn, int sumNum, List<GbDeviceChannel> chunk) {
        String key = key(deviceId, sn);
        Batch batch = cache.computeIfAbsent(key, k -> new Batch(deviceId, sn, sumNum));
        synchronized (batch) {
            batch.channels.addAll(chunk);
            batch.lastUpdateMs = System.currentTimeMillis();
            log.info("[通道目录] 分片累积 deviceId={} sn={} {}/{}", deviceId, sn, batch.channels.size(), sumNum);
            if (batch.channels.size() >= batch.sumNum) {
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
                log.warn("[通道目录] 分片超时丢弃 key={} 累计={}", e.getKey(), e.getValue().channels.size());
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
        public volatile long lastUpdateMs = System.currentTimeMillis();
        public volatile boolean done = false;

        public Batch(String deviceId, int sn, int sumNum) {
            this.deviceId = deviceId;
            this.sn = sn;
            this.sumNum = sumNum;
        }
    }
}
