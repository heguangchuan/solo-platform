package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.response.RecordItemResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RecordInfo 分片缓存
 *
 * <p>设备的 RecordInfo 回复会按 SumNum 分多个 MESSAGE 报文分批送回，必须先在内存缓存累计；
 * 收齐 SumNum 条后再一次性回调给 service 层的等待 future。</p>
 *
 * <p>按 {@code (deviceId, sn)} 作为 batch key — SN 由发起查询的 service 生成并存到 pending 表，
 * 响应到达时同 sn 命中同 batch。30 秒未收齐自动丢弃，避免设备发了一半掉线导致内存泄漏。</p>
 *
 * <p>分片去重：以 {@code startTime+endTime} 为 LinkedHashMap key —— 个别设备会重发同一段录像，
 * 用 map 而非 list 既能保序又能去重。</p>
 */
@Slf4j
@Component
public class RecordInfoDataCache {

    /** 30 秒以上未更新的 batch 视为僵死 */
    private static final long EXPIRE_MS = 30_000L;

    private final Map<String, Batch> cache = new ConcurrentHashMap<>();

    /**
     * 累加一批录像段，若达到 SumNum 返回完整 batch + 标记 done；否则返回 null。
     */
    public Batch accumulate(String deviceId, int sn, int sumNum, List<RecordItemResponse> chunk) {
        String key = key(deviceId, sn);
        Batch batch = cache.computeIfAbsent(key, k -> new Batch(deviceId, sn, sumNum));
        synchronized (batch) {
            for (RecordItemResponse item : chunk) {
                String dedupKey = (item.getStartTime() == null ? "" : item.getStartTime())
                        + "|" + (item.getEndTime() == null ? "" : item.getEndTime());
                batch.itemMap.put(dedupKey, item);
            }
            batch.lastUpdateMs = System.currentTimeMillis();
            log.info("[录像查询] 分片累积 deviceId={} sn={} {}/{}",
                    deviceId, sn, batch.itemMap.size(), sumNum);
            if (batch.itemMap.size() >= batch.sumNum) {
                batch.done = true;
                cache.remove(key);
            }
        }
        return batch.done ? batch : null;
    }

    /** 主动丢弃（service 超时回收时调用，避免内存泄漏） */
    public void drop(String deviceId, int sn) {
        cache.remove(key(deviceId, sn));
    }

    /** 定时清理过期 batch（DeviceOfflineDetector 之类的定时任务里调一下即可） */
    public void sweep() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> {
            if (now - e.getValue().lastUpdateMs > EXPIRE_MS) {
                log.warn("[录像查询] 分片超时丢弃 key={} 累计={}", e.getKey(), e.getValue().itemMap.size());
                return true;
            }
            return false;
        });
    }

    private static String key(String deviceId, int sn) {
        return deviceId + ":" + sn;
    }

    /** 一次 RecordInfo 查询的累积状态 */
    public static class Batch {
        public final String deviceId;
        public final int sn;
        public final int sumNum;
        /** LinkedHashMap 保序且按 (startTime|endTime) 去重 */
        public final Map<String, RecordItemResponse> itemMap = new LinkedHashMap<>();
        public volatile long lastUpdateMs = System.currentTimeMillis();
        public volatile boolean done = false;

        public Batch(String deviceId, int sn, int sumNum) {
            this.deviceId = deviceId;
            this.sn = sn;
            this.sumNum = sumNum;
        }

        /** 当前累积的录像段（按 startTime 升序） */
        public List<RecordItemResponse> items() {
            List<RecordItemResponse> list = new ArrayList<>(itemMap.values());
            list.sort((a, b) -> {
                String sa = a.getStartTime() == null ? "" : a.getStartTime();
                String sb = b.getStartTime() == null ? "" : b.getStartTime();
                return sa.compareTo(sb);
            });
            return list;
        }
    }
}
