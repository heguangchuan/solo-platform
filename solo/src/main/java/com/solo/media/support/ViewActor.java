package com.solo.media.support;

import com.solo.media.entity.GbViewRecord;

/**
 * 观看者身份：平台用户或开放 API 应用，随播放令牌带到 ZLM hook 里。
 */
public record ViewActor(int actorType, Long actorId, String actorName) {

    public static ViewActor user(Long userId, String username) {
        return new ViewActor(GbViewRecord.ACTOR_USER, userId, username);
    }

    public static ViewActor openApp(Long appId, String appName) {
        return new ViewActor(GbViewRecord.ACTOR_OPEN_APP, appId, appName);
    }
}
