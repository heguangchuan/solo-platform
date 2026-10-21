package com.solo.gb28181.transmit.cmd;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>solo 单机部署，级联态无需 Redis（与设备在线态用 Redis 作权威的用途不同，见 ADR-018）。
 * 仅维护两类瞬态：</p>
 * <ul>
 *   <li>{@code callId -> serverGbId}：一次 REGISTER 事务进行中，收到响应时按 Call-Id 回查属于哪个上级；</li>
 *   <li>{@code serverGbId -> 心跳连续超时次数}：连续 3 次判该上级离线。</li>
 * </ul>
 */
@Component
public class PlatformRegisterState {

    /** 连续心跳超时达到该阈值判离线 */
    public static final int KEEPALIVE_FAIL_THRESHOLD = 3;

    private final Map<String, PlatformRegisterTx> registerCallId = new ConcurrentHashMap<>();
    private final Map<String, Integer> keepaliveFail = new ConcurrentHashMap<>();

    // ---- 注册事务 callId 关联 ----

    public void markRegistering(String callId, String serverGbId, boolean register) {
        registerCallId.put(callId, new PlatformRegisterTx(serverGbId, register));
    }

    public PlatformRegisterTx txByCallId(String callId) {
        return registerCallId.get(callId);
    }

    public void clearRegistering(String callId) {
        registerCallId.remove(callId);
    }

    // ---- 心跳超时计数 ----

    /** 记一次心跳超时，返回累计次数 */
    public int incKeepaliveFail(String serverGbId) {
        return keepaliveFail.merge(serverGbId, 1, Integer::sum);
    }

    /** 心跳成功，清零 */
    public void resetKeepaliveFail(String serverGbId) {
        keepaliveFail.remove(serverGbId);
    }
}
