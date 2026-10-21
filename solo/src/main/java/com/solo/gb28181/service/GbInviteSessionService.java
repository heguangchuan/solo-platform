package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;

import java.util.List;

/**
 * <p>所有方法都按"运行时状态"语义对待：</p>
 * <ul>
 *     <li>{@link #save} 创建新会话（INVITING）</li>
 *     <li>{@link #update} 更新已有会话（READY / to-tag / URL 等）</li>
 *     <li>{@link #removeByStreamId} 会话结束（stop / fail / timeout / BYE）— 物理删除</li>
 *     <li>{@code getByXxx / list...}  反查与列举</li>
 * </ul>
 *
 * <p>Redis 数据布局见实现类 Javadoc。</p>
 */
public interface GbInviteSessionService {

    void save(GbInviteSession session);

    void update(GbInviteSession session);

    void removeByStreamId(String streamId);

    GbInviteSession getByStreamId(String streamId);

    GbInviteSession getByCallId(String callId);

    GbInviteSession getBySsrc(String ssrc);

    List<GbInviteSession> listActiveByDeviceAndChannel(String deviceId, String channelId);

    List<GbInviteSession> listAllActive();
}
