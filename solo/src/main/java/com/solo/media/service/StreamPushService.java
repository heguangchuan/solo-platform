package com.solo.media.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.media.entity.StreamPush;
import com.solo.media.request.StreamPushPageRequest;
import com.solo.media.response.StreamPushPlayUrlResponse;

import java.util.List;
import java.util.Map;

/**
 * 推流服务
 *
 * <p>负责：分页查询、强制中止、hook 回调时 upsert / 删除、on_server_started 兜底同步。</p>
 */
public interface StreamPushService extends IService<StreamPush> {

    Page<StreamPush> pageQuery(StreamPushPageRequest request);

    StreamPushPlayUrlResponse getPlayUrls(Long id);

    /**
     * 强制中止：调 ZLM close_streams，本地行标 pushing=0（真删还是留由 hook 补齐）。
     */
    void forceStop(Long id);

    /** 单条删除（前端手动清理离线记录用） */
    void deleteById(Long id);

    /** 批量删除 */
    void deleteBatch(List<Long> ids);

    /**
     * hook on_stream_changed(regist=true) 分发过来的注册事件，仅 originType∈PUSH 才会被调。
     * <p>upsert：无则插入，有则更新 pushing/reader/alive/schemas/origin_*。</p>
     */
    void onStreamRegist(String app, String stream, String mediaServerId, Map<String, Object> body);

    /**
     * hook on_stream_changed(regist=false) 分发过来的注销事件。
     * <p>找不到行直接忽略；找到行则物理删除（无国标绑定要保留的语义时另说，当前 solo 未启用级联）。</p>
     */
    void onStreamCancel(String app, String stream, String mediaServerId);

    /**
     * on_server_started 时兜底：拉 ZLM 当前所有流，与库对齐；
     * 数据库有但 ZLM 没有 → 删。
     * ZLM 有但库没有 → 不管（后续 on_stream_changed 会补，或用户不看就不入）。
     */
    void reconcileWithZlm(String mediaServerId);
}
