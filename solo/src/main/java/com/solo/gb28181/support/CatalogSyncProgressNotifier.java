package com.solo.gb28181.support;

import com.solo.gb28181.event.CatalogSyncProgressEvent;
import com.solo.websocket.SystemMessageWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Catalog 同步进度 → WebSocket 广播监听器。
 * <p>复用 {@link SystemMessageWebSocket#broadcast} 通道，消息格式：</p>
 * <pre>{@code
 * {
 *   "type": "GB_SYNC_PROGRESS",
 *   "data": {
 *     "deviceId": "...",
 *     "status": "RUNNING" | "SUCCESS" | "FAILED" | "IDLE",
 *     "received": 1234,
 *     "total": 5678,
 *     "percent": 21.7,
 *     "startedAtMs": 1700000000000,
 *     "lastUpdateAtMs": 1700000000000,
 *     "finishedAt": "2026-08-07 12:34:56" | null,
 *     "errorMsg": "..." | null,
 *     "duplicateCount": 0
 *   }
 * }
 * }</pre>
 *
 * <p>前端订阅后按 {@code data.deviceId} 过滤即可。本类只做"事件 → JSON → broadcast"，
 * 不做用户粒度的过滤（同步状态对登录用户都可见）。</p>
 */
@Slf4j
@Component
public class CatalogSyncProgressNotifier {

    @EventListener
    public void onProgress(CatalogSyncProgressEvent event) {
        CatalogSyncProgress p = event.getProgress();
        if (p == null) return;
        try {
            String json = "{\"type\":\"GB_SYNC_PROGRESS\",\"data\":"
                    + toJson(p)
                    + "}";
            int sent = SystemMessageWebSocket.broadcast(json);
            if (log.isDebugEnabled()) {
                log.debug("[通道目录] 进度推送 deviceId={} status={} 收到在线={}人",
                        p.getDeviceId(), p.getStatus(), sent);
            }
        } catch (Exception e) {
            log.warn("[通道目录] 推送进度失败 deviceId={} err={}", p.getDeviceId(), e.getMessage());
        }
    }

    private String toJson(CatalogSyncProgress p) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
                .append("\"deviceId\":\"").append(esc(p.getDeviceId())).append('"')
                .append(",\"status\":\"").append(p.getStatus()).append('"')
                .append(",\"received\":").append(p.getReceived())
                .append(",\"total\":").append(p.getTotal())
                .append(",\"percent\":").append(String.format("%.2f", p.getPercent()))
                .append(",\"startedAtMs\":").append(p.getStartedAtMs())
                .append(",\"lastUpdateAtMs\":").append(p.getLastUpdateAtMs());
        if (p.getFinishedAt() != null) {
            sb.append(",\"finishedAt\":\"").append(esc(p.getFinishedAt())).append('"');
        }
        if (p.getErrorMsg() != null) {
            sb.append(",\"errorMsg\":\"").append(esc(p.getErrorMsg())).append('"');
        }
        sb.append(",\"duplicateCount\":").append(p.getDuplicateCount())
                .append('}');
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
