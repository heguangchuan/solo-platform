package com.solo.gb28181.support;

import org.springframework.context.ApplicationEvent;

// Registry 发现下载超时后发个事件，让 Service 收端口、SSRC 和会话。
// 用事件是为了避开 Service -> Registry -> Service 的循环依赖。
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
