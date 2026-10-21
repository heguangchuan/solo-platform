package com.solo.gb28181.sip;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主动请求 -> 等响应的订阅管理器。
 * 按 Call-Id + CSeq 存，SipProcessorObserver 收到响应对上回调。
 * sweep() 定时清超时的，每 5 秒扫一次。
 *
 * 之前想过用 Guava Cache 带过期回调，但这边量不大（也就几百个并发请求），
 * ConcurrentHashMap + 定时扫够用了，少引个依赖。
 */
@Slf4j
@Component
public class SipSubscribe {

    // key = callId + ":" + cseq，见 SipSubscribeEvent#getKey
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

    /**
     * 清理超时订阅。超时的触发 onTimeout 然后删掉。
     * 注意 removeIf 里直接跑回调，别搞太重的逻辑，
     * 真有耗时操作让回调自己丢线程池去。
     */
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
