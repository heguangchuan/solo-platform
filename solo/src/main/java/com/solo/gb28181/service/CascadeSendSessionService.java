package com.solo.gb28181.service;

import com.solo.gb28181.entity.CascadeSendSession;

import java.util.List;

/**
 * 级联推流会话存储（Redis）。仿 {@link GbInviteSessionService}：纯 CRUD + 多维反查索引，不含业务编排。
 * <p>业务编排（识别上级 / 取流 / startSendRtp / 停流清理）在
 * {@link com.solo.gb28181.service.impl.CascadeSendService}。</p>
 */
public interface CascadeSendSessionService {

    void save(CascadeSendSession s);

    /** 仅覆盖主体（callId/srcStream 索引不变） */
    void update(CascadeSendSession s);

    void removeBySendStreamId(String sendStreamId);

    CascadeSendSession getBySendStreamId(String sendStreamId);

    CascadeSendSession getByCallId(String callId);

    /** 同一路源流可能被多个上级点播，返回全部 */
    List<CascadeSendSession> listBySrcStream(String srcStream);

    List<CascadeSendSession> listAllActive();

    /**
     * 分配一个本地发送端口（Redis 自增+回绕，落在 [start, end] 内）。仅粗略避重，ZLM 侧最终裁决占用。
     */
    int acquireSendPort(int start, int end);
}
