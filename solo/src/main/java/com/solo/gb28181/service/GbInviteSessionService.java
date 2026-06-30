package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbInviteSession;

import java.util.List;

/**
 * 点播 / 回放 / 下载会话服务（Redis 主存）
 *
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

    /** 新建会话；会同步建立 callId/ssrc/dev:chan 反查索引并加入全集 Set */
    void save(GbInviteSession session);

    /** 更新会话；以 streamId 为主键覆盖；按需同步索引（to-tag / url 等通常无索引变化） */
    void update(GbInviteSession session);

    /** 按 streamId 物理删除：删主体 + 删所有索引 + 从全集 Set 移除 */
    void removeByStreamId(String streamId);

    /** 按 streamId 查 */
    GbInviteSession getByStreamId(String streamId);

    /** 按 INVITE Dialog Call-Id 反查 */
    GbInviteSession getByCallId(String callId);

    /** 按 SSRC 反查 */
    GbInviteSession getBySsrc(String ssrc);

    /** 取设备/通道下所有未结束会话（运行时表里只放未结束的，理论上单条或空） */
    List<GbInviteSession> listActiveByDeviceAndChannel(String deviceId, String channelId);

    /** 列出全部未结束会话（启动清扫 / SsrcFactory 预占用） */
    List<GbInviteSession> listAllActive();
}
