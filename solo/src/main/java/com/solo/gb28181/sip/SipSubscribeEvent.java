package com.solo.gb28181.sip;

import lombok.Getter;
import lombok.Setter;

import javax.sip.ResponseEvent;
import java.util.function.Consumer;

/**
 * SIP 异步事件
 * <p>主动发起一个 SIP 请求后，业务侧需要等响应到达。{@link SipSubscribe} 用 Call-Id+CSeq 作为 key
 * 记录回调，{@link SipProcessorObserver} 收到响应时取出回调触发。</p>
 */
public class SipSubscribeEvent {

    /** 唯一 key：Call-Id + CSeq */
    @Getter
    private final String key;

    /** 2xx 回调 */
    @Getter @Setter
    private Consumer<ResponseEvent> onOk;

    /** 4xx/5xx/6xx 回调 */
    @Getter @Setter
    private Consumer<ResponseEvent> onError;

    /** 超时回调（事务超时或主动取消时触发） */
    @Getter @Setter
    private Runnable onTimeout;

    /** 注册时间戳（用于过期清理） */
    @Getter
    private final long createTimeMs = System.currentTimeMillis();

    /** 超时（毫秒），0 表示不主动超时 */
    @Getter
    private final long timeoutMs;

    public SipSubscribeEvent(String key, long timeoutMs) {
        this.key = key;
        this.timeoutMs = timeoutMs;
    }
}
