package com.solo.gb28181.processor.request;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SUBSCRIBE 订阅内存表（ADR-029）。
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
     * 列出某事件类型下的全部订阅。catalog 变更时遍历此集合给所有订阅方推 NOTIFY。
     */
    public Collection<SubscribeInfo> listByCmdType(String cmdType) {
        if (cmdType == null) {
            return java.util.Collections.emptyList();
        }
        return map.values().stream()
                .filter(s -> cmdType.equals(s.getCmdType()))
                .toList();
    }

    /** 当前订阅总数——排查/测试用，不暴露给前端。 */
    public int size() {
        return map.size();
    }
}
