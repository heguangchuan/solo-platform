package com.solo.gb28181.service;

import com.solo.gb28181.entity.CascadeSendSession;

import java.util.List;

/**
 * 纯 CRUD + 多维反查索引，不含业务编排。
 * <p>业务编排（识别上级 / 取流 / startSendRtp / 停流清理）在
 * {@link com.solo.gb28181.service.impl.CascadeSendService}。</p>
 */
public interface CascadeSendSessionService {

    void save(CascadeSendSession s);

    void update(CascadeSendSession s);

    void removeBySendStreamId(String sendStreamId);

    CascadeSendSession getBySendStreamId(String sendStreamId);

    CascadeSendSession getByCallId(String callId);

    List<CascadeSendSession> listBySrcStream(String srcStream);

    List<CascadeSendSession> listAllActive();

    int acquireSendPort(int start, int end);
}
