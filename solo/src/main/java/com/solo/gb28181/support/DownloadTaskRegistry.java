package com.solo.gb28181.support;

import com.solo.gb28181.response.DownloadResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 任务只放内存，重启就丢。非终态 30 分钟强停；终态留 1 小时给前端查，再删。
@Slf4j
@Component
public class DownloadTaskRegistry {

    public static final Duration INVITE_TIMEOUT = Duration.ofMinutes(30);

    public static final Duration FINAL_RETENTION = Duration.ofHours(1);

    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    public DownloadTaskRegistry(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        EvictionScheduler.scheduleAtFixedRate(this::sweep, SWEEP_INTERVAL_MS, SWEEP_INTERVAL_MS);
    }

    public void put(DownloadTask task) {
        if (task == null || task.getStreamId() == null) {
            return;
        }
        tasks.put(task.getStreamId(), task);
    }

    public DownloadTask get(String streamId) {
        if (streamId == null) {
            return null;
        }
        return tasks.get(streamId);
    }

    public Collection<DownloadTask> all() {
        return tasks.values();
    }

    public void markStatus(String streamId, String status, String errorMsg) {
        DownloadTask t = tasks.get(streamId);
        if (t == null) {
            return;
        }
        t.setStatus(status);
        if (errorMsg != null) {
            t.setErrorMsg(errorMsg);
        }
        if (DownloadResultResponse.STATUS_READY.equals(status)
                || DownloadResultResponse.STATUS_FAILED.equals(status)
                || DownloadResultResponse.STATUS_TIMEOUT.equals(status)) {
            t.setReadyAt(LocalDateTime.now());
        }
        if (DownloadResultResponse.STATUS_INVITING.equals(status) && t.getInviteAt() == null) {
            t.setInviteAt(LocalDateTime.now());
        }
    }

    public void remove(String streamId) {
        tasks.remove(streamId);
    }

    // 每 5s 扫一轮：非终态超时发事件，终态保留期到了就删。
    void sweep() {
        LocalDateTime now = LocalDateTime.now();
        for (DownloadTask t : tasks.values()) {
            String s = t.getStatus();
            if (s == null) {
                continue;
            }
            if (DownloadResultResponse.STATUS_INVITING.equals(s)
                    || DownloadResultResponse.STATUS_RECORDING.equals(s)) {
                if (t.getInviteAt() == null) {
                    continue;
                }
                if (Duration.between(t.getInviteAt(), now).compareTo(INVITE_TIMEOUT) > 0) {
                    String sid = t.getStreamId();
                    log.warn("[录像下载] 任务超时 streamId={} 状态:{} 已耗时:{}ms",
                            sid, s, Duration.between(t.getInviteAt(), now).toMillis());
                    t.setStatus(DownloadResultResponse.STATUS_TIMEOUT);
                    t.setErrorMsg("下载超过 30 分钟未完成，已强制结束");
                    t.setReadyAt(now);
                    try {
                        eventPublisher.publishEvent(new DownloadTimeoutEvent(this, sid));
                    } catch (Exception e) {
                        log.warn("[录像下载] 发布超时事件失败 streamId={} err={}", sid, e.getMessage());
                    }
                }
            } else if (DownloadResultResponse.STATUS_READY.equals(s)
                    || DownloadResultResponse.STATUS_FAILED.equals(s)
                    || DownloadResultResponse.STATUS_TIMEOUT.equals(s)) {
                if (t.getReadyAt() != null
                        && Duration.between(t.getReadyAt(), now).compareTo(FINAL_RETENTION) > 0) {
                    tasks.remove(t.getStreamId());
                }
            }
        }
    }

    private static final class EvictionScheduler {
        private static final ScheduledExecutorService EXECUTOR =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "gb-download-sweep");
                    t.setDaemon(true);
                    return t;
                });

        static void scheduleAtFixedRate(Runnable r, long initialDelayMs, long periodMs) {
            try {
                EXECUTOR.scheduleAtFixedRate(r, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("[录像下载] sweep 调度失败", e);
            }
        }
    }
}
