package com.solo.gb28181.subscribe;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.transmit.cmd.SipCommander;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ToHeader;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 设备目录订阅管理
 * <p>设备上线后按配置周期发起 Catalog 订阅，到期自动续期，离线时退订。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceCatalogSubscribeManager {

    private static final String LOG_TAG = "[目录订阅]";

    public static final int MIN_CYCLE_SECONDS = 30;

    private static final double RENEW_ADVANCE_RATIO = 0.1;

    private static final long RETRY_DELAY_MS = 2000L;

    private static final int MAX_RETRY = 3;

    private static final long SUBSCRIBE_TIMEOUT_MS = 10_000L;

    private final SipCommander sipCommander;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final GbDeviceMapper deviceMapper;

    private final ConcurrentHashMap<String, SubscribeTaskState> tasks = new ConcurrentHashMap<>();

    /**
     * 启动订阅。已存在则先停再启，保证用最新周期。
     */
    public void start(String deviceId) {
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device == null) {
            log.warn("{} 启动失败：设备不存在 deviceId={}", LOG_TAG, deviceId);
            return;
        }
        int cycle = normalizeCycle(device.getSubscribeCycleForCatalog());
        if (cycle <= 0) {
            return;
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            return;
        }
        stopInternal(deviceId, false);
        SubscribeTaskState state = new SubscribeTaskState(device, cycle);
        tasks.put(deviceId, state);
        state.renewFuture = taskScheduler.schedule(() -> doSubscribe(state), Instant.now());
        log.info("{} 启动订阅 deviceId={} cycle={}s", LOG_TAG, deviceId, cycle);
    }

    /**
     * 停止订阅，并发退订。
     */
    public void stop(String deviceId) {
        stopInternal(deviceId, true);
    }

    /**
     * 设备编辑后重载订阅，周期变化时重启。
     */
    public void reload(String deviceId) {
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
        if (device == null) {
            stop(deviceId);
            return;
        }
        int newCycle = normalizeCycle(device.getSubscribeCycleForCatalog());
        SubscribeTaskState existing = tasks.get(deviceId);

        if (newCycle <= 0) {
            if (existing != null) {
                stop(deviceId);
            }
            return;
        }
        if (existing == null || existing.cycleSeconds != newCycle) {
            start(deviceId);
        }
    }

    private void stopInternal(String deviceId, boolean sendUnsubscribe) {
        SubscribeTaskState state = tasks.remove(deviceId);
        if (state == null) {
            return;
        }
        if (state.renewFuture != null && !state.renewFuture.isDone()) {
            state.renewFuture.cancel(false);
        }
        if (sendUnsubscribe && state.callId != null && state.device != null) {
            try {
                sipCommander.catalogSubscribe(
                        state.device, 0,
                        state.callId, state.fromTag, state.toTag,
                        null, null, SUBSCRIBE_TIMEOUT_MS);
                log.info("{} 发送退订 deviceId={}", LOG_TAG, deviceId);
            } catch (Exception e) {
                log.warn("{} 退订发送失败 deviceId={}: {}", LOG_TAG, deviceId, e.getMessage());
            }
        }
        log.info("{} 停止订阅 deviceId={}", LOG_TAG, deviceId);
    }

    private void doSubscribe(SubscribeTaskState state) {
        if (state.stopped) return;
        try {
            SIPRequest req = sipCommander.catalogSubscribe(
                    state.device, state.cycleSeconds,
                    state.callId, state.fromTag, state.toTag,
                    okEv -> onSubscribeOk(state, okEv),
                    errEv -> onSubscribeError(state, errEv),
                    SUBSCRIBE_TIMEOUT_MS);
            if (state.callId == null && req != null) {
                state.callId = req.getCallIdHeader();
                state.fromTag = req.getFromHeader().getTag();
            }
        } catch (Exception e) {
            log.error("{} 订阅异常 deviceId={}: {}", LOG_TAG, state.device.getDeviceId(), e.getMessage(), e);
            onSubscribeError(state, null);
        }
    }

    private void onSubscribeOk(SubscribeTaskState state, ResponseEvent event) {
        if (state.stopped) return;
        state.failCount.set(0);
        if (event != null && event.getResponse() != null) {
            ToHeader toHeader = (ToHeader) event.getResponse().getHeader(ToHeader.NAME);
            if (toHeader != null && toHeader.getTag() != null) {
                state.toTag = toHeader.getTag();
            }
        }
        log.info("{} 订阅成功 deviceId={} 下次续期={}s 后",
                LOG_TAG, state.device.getDeviceId(), renewDelaySeconds(state.cycleSeconds));
        // 调度续期（提前 10%）
        long delayMs = renewDelaySeconds(state.cycleSeconds) * 1000L;
        state.renewFuture = taskScheduler.schedule(
                () -> doSubscribe(state),
                Instant.now().plusMillis(delayMs));
    }

    private void onSubscribeError(SubscribeTaskState state, ResponseEvent event) {
        if (state.stopped) return;
        int count = state.failCount.incrementAndGet();
        String reason = "未知";
        if (event != null && event.getResponse() != null) {
            int status = event.getResponse().getStatusCode();
            String phrase = event.getResponse().getReasonPhrase();
            reason = status + " " + phrase;
        }
        if (count > MAX_RETRY) {
            log.warn("{} 订阅连续失败 {} 次，放弃 deviceId={} 最后原因: {}",
                    LOG_TAG, MAX_RETRY, state.device.getDeviceId(), reason);
            tasks.remove(state.device.getDeviceId(), state);
            return;
        }
        log.warn("{} 订阅失败（第 {} 次），{}ms 后重试 deviceId={} 原因: {}",
                LOG_TAG, count, RETRY_DELAY_MS, state.device.getDeviceId(), reason);
        state.renewFuture = taskScheduler.schedule(
                () -> doSubscribe(state),
                Instant.now().plusMillis(RETRY_DELAY_MS));
    }

    private static int normalizeCycle(Integer cycle) {
        if (cycle == null || cycle <= 0) return 0;
        return Math.max(cycle, MIN_CYCLE_SECONDS);
    }

    private static int renewDelaySeconds(int cycleSeconds) {
        int advance = (int) (cycleSeconds * RENEW_ADVANCE_RATIO);
        return Math.max(cycleSeconds - Math.max(advance, 5), 5);
    }

    @PreDestroy
    public void destroy() {
        for (String deviceId : tasks.keySet()) {
            stopInternal(deviceId, false);
        }
        log.info("{} 管理器已关闭，清理订阅数={}", LOG_TAG, tasks.size());
    }

    private static class SubscribeTaskState {
        final GbDevice device;
        final int cycleSeconds;
        CallIdHeader callId;
        String fromTag;
        String toTag;
        final AtomicInteger failCount = new AtomicInteger(0);
        volatile boolean stopped;
        volatile ScheduledFuture<?> renewFuture;

        SubscribeTaskState(GbDevice device, int cycleSeconds) {
            this.device = device;
            this.cycleSeconds = cycleSeconds;
        }
    }
}
