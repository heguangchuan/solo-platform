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

/**
 * 录像下载任务注册表（内存表，按 streamId 索引）。
 *
 * <h3>三个时间窗</h3>
 * <ul>
 *   <li><b>邀请超时</b>：{@link #INVITE_TIMEOUT} 30 分钟。INVITE 已发但流没就绪 / 设备没应答，
 *       自动转 {@link DownloadResultResponse#STATUS_TIMEOUT}，并触发  回调
 *       让调用方收尾（关 RTP 端口、回收 SSRC）。</li>
 *   <li><b>录制中超时</b>：同样 30 分钟。ZLM 在录但文件没落盘（设备断流、ZLM 卡死），
 *       强制判 TIMEOUT。</li>
 *   <li><b>完成保留</b>：{@link #FINAL_RETENTION} 1 小时。READY / FAILED / TIMEOUT 后保留一小时
 *       供前端查状态，1 小时后扫掉。</li>
 * </ul>
 *
 * <p><b>为什么不存 DB</b>：与 S5 报警的"不入库"原则对齐，下载记录是临时状态，
 * 高频创建/清理不该污染业务表。重启即丢是预期行为。</p>
 *
 * <p><b>为什么用 daemon 单线程扫</b>：5 秒扫一次的频率对单实例下几十个并发下载任务足够，
 * 不抢业务线程；用 {@link ConcurrentHashMap} 写入路径无锁。</p>
 */
@Slf4j
@Component
public class DownloadTaskRegistry {

    /** 邀请 / 录制中总超时：30 分钟。覆盖单段录像下载 + 文件落盘 + MinIO 上传 */
    public static final Duration INVITE_TIMEOUT = Duration.ofMinutes(30);

    /** 终态（READY / FAILED / TIMEOUT）后保留：1 小时。给前端拉取状态的时间窗 */
    public static final Duration FINAL_RETENTION = Duration.ofHours(1);

    /** 扫表频率：5 秒 */
    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    public DownloadTaskRegistry(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        EvictionScheduler.scheduleAtFixedRate(this::sweep, SWEEP_INTERVAL_MS, SWEEP_INTERVAL_MS);
    }

    /**
     * 注册新任务（创建时调用）。已存在同名 streamId 时覆盖 —— streamId 由
     * {@code SsrcFactory.allocate + streamNumber 拼接} 生成，重复概率极低。
     */
    public void put(DownloadTask task) {
        if (task == null || task.getStreamId() == null) {
            return;
        }
        tasks.put(task.getStreamId(), task);
    }

    /** 按 streamId 查任务，未命中返回 null */
    public DownloadTask get(String streamId) {
        if (streamId == null) {
            return null;
        }
        return tasks.get(streamId);
    }

    /** 全部任务（管理 / 调试用，生产环境不要遍历） */
    public Collection<DownloadTask> all() {
        return tasks.values();
    }

    /**
     * 推进状态。带错误信息时一并写入。
     * 终态（READY/FAILED/TIMEOUT）会刷新 {@code readyAt}，让 1 小时后清理。
     */
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

    /**
     * 删任务（仅在确认已上传 MinIO 且前端已取走后由调用方主动删，避免误用）。
     * 一般不调 —— 让 sweep 自动清。
     */
    public void remove(String streamId) {
        tasks.remove(streamId);
    }

    /**
     * 扫描：
     * <ol>
     *   <li>非终态超过 {@link #INVITE_TIMEOUT} → 标 TIMEOUT 并发
     *       {@link DownloadTimeoutEvent}（由 {@code GbDownloadServiceImpl.onDownloadTimeout} 订阅）</li>
     *   <li>终态超过 {@link #FINAL_RETENTION} → 删</li>
     * </ol>
     */
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
                    // 发布超时事件：Service 订阅后做资源清扫（关 ZLM 端口、释放 SSRC、删会话）
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

    /** 单线程 daemon 定时器 */
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
