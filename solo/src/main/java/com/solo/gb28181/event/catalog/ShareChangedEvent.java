package com.solo.gb28181.event.catalog;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 目录共享关系变化事件。
 * <p>{@code gb_platform_share} 增删后发布，供级联订阅方按最新共享目录推送 NOTIFY。</p>
 */
@Getter
public class ShareChangedEvent extends ApplicationEvent {

    /** 上级平台主键（gb_parent_platform.id） */
    private final Long platformId;

    public ShareChangedEvent(Object source, Long platformId) {
        super(source);
        this.platformId = platformId;
    }
}
