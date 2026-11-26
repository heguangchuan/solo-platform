package com.solo.gb28181.processor.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 同一上级同一事件重复 SUBSCRIBE 就是续订，直接覆盖。
// 只放内存，重启后等对端重新订阅。
@Component
public class SubscribeHolder {

    private static final Logger log = LoggerFactory.getLogger(SubscribeHolder.class);

    private final ConcurrentHashMap<String, SubscribeInfo> map = new ConcurrentHashMap<>();

    private static final String SEP = ":";

    private static String key(String platformId, String cmdType) {
        return platformId + SEP + cmdType;
    }

    public void put(SubscribeInfo info) {
        if (info == null || info.getPlatformId() == null || info.getCmdType() == null) {
            return;
        }
        map.put(key(info.getPlatformId(), info.getCmdType()), info);
    }

    public SubscribeInfo remove(String platformId, String cmdType) {
        if (platformId == null || cmdType == null) {
            return null;
        }
        return map.remove(key(platformId, cmdType));
    }

    public Optional<SubscribeInfo> get(String platformId, String cmdType) {
        if (platformId == null || cmdType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(key(platformId, cmdType)));
    }

    // 这里只过滤不删；GPS 上报很勤，过期清理由定时任务统一做。
    public Collection<SubscribeInfo> listByCmdType(String cmdType) {
        if (cmdType == null) {
            return java.util.Collections.emptyList();
        }
        LocalDateTime now = LocalDateTime.now();
        return map.values().stream()
                .filter(s -> cmdType.equals(s.getCmdType()))
                .filter(s -> !expired(s, now))
                .toList();
    }

    public void sweepExpired() {
        LocalDateTime now = LocalDateTime.now();
        int before = map.size();
        map.values().removeIf(s -> expired(s, now));
        int removed = before - map.size();
        if (removed > 0) {
            log.info("[订阅管理] 过期订阅清理 清理条数={} 剩余条数={}", removed, map.size());
        }
    }

    public Optional<SubscribeInfo> getActive(String platformId, String cmdType) {
        SubscribeInfo info = get(platformId, cmdType).orElse(null);
        if (info == null || expired(info, LocalDateTime.now())) {
            return Optional.empty();
        }
        return Optional.of(info);
    }

    private static boolean expired(SubscribeInfo s, LocalDateTime now) {
        return s == null || s.getAcceptedAt() == null
                || now.isAfter(s.getAcceptedAt().plusSeconds(Math.max(s.getExpires(), 0)));
    }

    public int size() {
        return map.size();
    }
}
