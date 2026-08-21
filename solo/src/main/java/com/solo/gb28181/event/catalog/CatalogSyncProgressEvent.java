package com.solo.gb28181.event.catalog;

import com.solo.gb28181.support.CatalogSyncProgress;
import org.springframework.context.ApplicationEvent;

/**
 * Catalog 同步进度事件（Spring ApplicationEvent）。
 * <p>由 {@link com.solo.gb28181.processor.request.message.response.CatalogDataCache} 在
 * 阈值 flush / 收尾时发布。当前订阅者：
 * {@link com.solo.gb28181.support.CatalogSyncStatusService} 落 Redis（供 {@code GET /sync-status} 接口读）+
 * 收尾时释放同步锁。后续若加 WebSocket 推送 / 其它下游消费，<b>新写 listener</b> 订阅本事件即可，
 * Spring {@code @EventListener} 多对一干净，不需预先在事件侧占名。</p>
 */
public class CatalogSyncProgressEvent extends ApplicationEvent {

    private final CatalogSyncProgress progress;

    public CatalogSyncProgressEvent(CatalogSyncProgress progress) {
        super(progress);
        this.progress = progress;
    }

    public CatalogSyncProgress getProgress() {
        return progress;
    }
}
