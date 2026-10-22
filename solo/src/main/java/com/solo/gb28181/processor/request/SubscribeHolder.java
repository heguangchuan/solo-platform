package com.solo.gb28181.processor.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>本平台作为下级时收到的上级 SUBSCRIBE 订阅关系都登记在此，供后续变更 NOTIFY 推送（ADR-030）取用。
 * ConcurrentHashMap 按 {@code (platformId, cmdType)} 维度索引——同上级对同事件重复 SUBSCRIBE 视为续订
 * （覆盖 expires / acceptedAt），符合 RFC 6665 语义。</p>
 * <p><b>不持久化</b>：进程重启即清空，上级按 SIP 自身重传机制（一般 30-60s 一次）会重发 SUBSCRIBE。
 * 该简化是 GB/T 28181 协议默许行为，避免引入 Redis 持久化层。</p>
 *
 * @see SubscribeInfo
 */
@Component
public class SubscribeHolder {

    private static final Logger log = LoggerFactory.getLogger(SubscribeHolder.class);

    private final ConcurrentHashMap<String, SubscribeInfo> map = new ConcurrentHashMap<>();

    /** 主键分隔符。platformId / cmdType 都是 GB 协议字段，不含 {@code ':'}，无需转义。 */
    private static final String SEP = ":";

    private static String key(String platformId, String cmdType) {
        return platformId + SEP + cmdType;
    }

    /**
     * 登记/续订一条订阅。同 {@code (platformId, cmdType)} 重复调用视为续订。
     */
    public void put(SubscribeInfo info) {
        if (info == null || info.getPlatformId() == null || info.getCmdType() == null) {
            return;
        }
        map.put(key(info.getPlatformId(), info.getCmdType()), info);
    }

    /**
     * 移除一条订阅。退订（Expires=0）走此路径。
     * @return 被移除的记录（若不存在则返回 {@code null}）
     */
    public SubscribeInfo remove(String platformId, String cmdType) {
        if (platformId == null || cmdType == null) {
            return null;
        }
        return map.remove(key(platformId, cmdType));
    }

    /**
     * 取单条订阅。NOTIFY 推送时按此定位发送目标。
     */
    public Optional<SubscribeInfo> get(String platformId, String cmdType) {
        if (platformId == null || cmdType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(key(platformId, cmdType)));
    }

    /**
     * 列出某事件类型下<b>未过期</b>的订阅。catalog / GPS / 报警 变更时遍历此集合给所有订阅方推 NOTIFY。
     * <p>过期清理由 {@code sweepExpired()} 定时执行，不在此方法里做——GPS 上报频率高（5-30s/次），
     * 每次 list 都扫全表+删是浪费。</p>
     */
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

    /**
     * 清理所有已过期的订阅记录，避免内存无限增长。由定时任务调用。
     */
    public void sweepExpired() {
        LocalDateTime now = LocalDateTime.now();
        int before = map.size();
        map.values().removeIf(s -> expired(s, now));
        int removed = before - map.size();
        if (removed > 0) {
            log.info("[订阅管理] 过期订阅清理 清理条数={} 剩余条数={}", removed, map.size());
        }
    }

    /**
     * 取某上级某事件类型下<b>未过期</b>的订阅。
     */
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

    /** 当前订阅总数——排查/测试用，不暴露给前端。 */
    public int size() {
        return map.size();
    }
}
