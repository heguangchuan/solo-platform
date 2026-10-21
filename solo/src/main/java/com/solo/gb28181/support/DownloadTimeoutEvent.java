package com.solo.gb28181.support;

import org.springframework.context.ApplicationEvent;

/**
 * <p>{@link DownloadTaskRegistry#sweep()} 命中超时任务时发布（{@code status=INVITING/RECORDING} 超 30 分钟）。
 * 由 {@link com.solo.gb28181.service.impl.GbDownloadServiceImpl#onDownloadTimeout} 订阅处理，
 * 负责关 ZLM 端口、释放 SSRC、删会话、清理资源。</p>
 *
 * <h3>为什么用 Event 不用接口回调</h3>
 * <p>避免 Service → Registry → Service 循环依赖（Registry 注入回调接口、唯一实现是 Service）：
 * 改为 Registry 发布 Event、Service 订阅，Spring Event 机制天然解耦，零依赖。
 * 与项目现有 {@code AlarmEvent} / {@code PositionEvent} 模式一致。</p>
 */
public class DownloadTimeoutEvent extends ApplicationEvent {

    private final String streamId;

    public DownloadTimeoutEvent(Object source, String streamId) {
        super(source);
        this.streamId = streamId;
    }

    public String getStreamId() {
        return streamId;
    }
}
