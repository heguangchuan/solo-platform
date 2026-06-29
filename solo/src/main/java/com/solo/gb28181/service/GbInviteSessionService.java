package com.solo.gb28181.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.gb28181.entity.GbInviteSession;

import java.util.List;

/**
 * 点播/回放会话服务（S2.1 仅暴露存取，调度逻辑由后续 PlayService 编排）
 */
public interface GbInviteSessionService extends IService<GbInviteSession> {

    /**
     * 按 streamId 查会话
     */
    GbInviteSession getByStreamId(String streamId);

    /**
     * 按 callId 查会话（INVITE Dialog 关联）
     */
    GbInviteSession getByCallId(String callId);

    /**
     * 按 ssrc 查会话（ZLM hook 上来时按此键反查）
     */
    GbInviteSession getBySsrc(String ssrc);

    /**
     * 取设备/通道下所有未结束（INVITING/READY）的会话
     */
    List<GbInviteSession> listActiveByDeviceAndChannel(String deviceId, String channelId);

    /**
     * 列出全部未结束的会话（启动时 SsrcFactory 预占用）
     */
    List<GbInviteSession> listAllActive();
}
