package com.solo.gb28181.event;

import com.solo.gb28181.support.CatalogSyncProgress;
import org.springframework.context.ApplicationEvent;

/**
 * Catalog 同步进度事件（Spring ApplicationEvent）。
 * <p>由 {@link com.solo.gb28181.processor.request.message.response.CatalogDataCache} 在
 * 阈值 flush / 收尾时发布，{@link CatalogSyncStatusService} 订阅以更新 Redis，
 * {@link CatalogSyncProgressListener} 订阅以推到 WebSocket。</p>
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
