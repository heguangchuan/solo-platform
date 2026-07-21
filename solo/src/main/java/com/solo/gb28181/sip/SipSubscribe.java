package com.solo.gb28181.sip;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIP 订阅管理器
 * <p>主动发起请求后注册一个 {@link SipSubscribeEvent}，{@link SipProcessorObserver}
 * 收到对应响应（按 Call-Id+CSeq 匹配）后回调并清理。</p>
 * <p>过期清理由 {@link com.solo.gb28181.task.GbScheduledTasks} 每 5 秒调 {@link #sweep()}：
 * 移除已超过 timeout 的订阅并触发 onTimeout。</p>
 */
@Slf4j
@Component
public class SipSubscribe {

    private final Map<String, SipSubscribeEvent> subscribes = new ConcurrentHashMap<>();

    public void add(SipSubscribeEvent event) {
        subscribes.put(event.getKey(), event);
    }

    public SipSubscribeEvent get(String key) {
        return subscribes.get(key);
    }

    public void remove(String key) {
        subscribes.remove(key);
    }

    public int size() {
        return subscribes.size();
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        subscribes.entrySet().removeIf(e -> {
            SipSubscribeEvent ev = e.getValue();
            if (ev.getTimeoutMs() > 0 && now - ev.getCreateTimeMs() > ev.getTimeoutMs()) {
                log.warn("[响应订阅] 超时回收: key={}", e.getKey());
                if (ev.getOnTimeout() != null) {
                    try { ev.getOnTimeout().run(); } catch (Exception ex) { log.error("onTimeout 异常", ex); }
                }
                return true;
            }
            return false;
        });
    }
}
