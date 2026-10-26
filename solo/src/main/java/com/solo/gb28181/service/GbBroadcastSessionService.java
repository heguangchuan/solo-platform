package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbBroadcastSession;

import java.util.List;

/**
 * 纯 CRUD + 多维反查索引，不含业务编排。
 * <p>业务编排（发通知 / 应答设备 INVITE / startSendRtp / 停流清理）在
 * {@link com.solo.gb28181.service.impl.GbBroadcastService}。</p>
 */
public interface GbBroadcastSessionService {

    void save(GbBroadcastSession s);

    void update(GbBroadcastSession s);

    void remove(String broadcastStreamId);

    GbBroadcastSession get(String broadcastStreamId);

    GbBroadcastSession getByCallId(String callId);

    /** 级联喊话时按"上级推音频那路 Dialog 的 callId"反查会话 */
    GbBroadcastSession getByParentCallId(String parentCallId);

    List<GbBroadcastSession> listBySrcStream(String srcStream);

    List<GbBroadcastSession> listAllActive();

    int acquireSendPort(int start, int end);
}
