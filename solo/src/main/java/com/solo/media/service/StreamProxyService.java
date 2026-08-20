package com.solo.media.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.media.entity.StreamProxy;
import com.solo.media.request.StreamProxyPageRequest;
import com.solo.media.response.StreamProxyPlayUrlResponse;

import java.util.List;

/**
 * 拉流代理服务
 *
 * <h3>与 ONVIF 点播的关系</h3>
 * <p>底层同样是 {@code ZlmRestClient.addStreamProxy}，但 ONVIF 走瞬时点播（Redis 会话记账、无观看即回收）；
 * 本模块是"管理台配置的持久代理"，进程重启后自动恢复。</p>
 *
 * <h3>状态对账</h3>
 * <p>ZLM 对 addStreamProxy 创建的流不像 push 那样有 on_stream_changed 兜底，
 * 源流挂了 / ZLM 重启 / 网络分区都不会主动通知，本服务 DB 里的 {@code status} 会一直停在 1。
 * 状态真实性靠 {@link #reconcileWithZlm} 兜底，由定时任务 + on_server_started hook 触发。</p>
 */
public interface StreamProxyService extends IService<StreamProxy> {

    Page<StreamProxy> pageQuery(StreamProxyPageRequest request);

    /**
     * 新增或更新代理配置。落库后自动尝试启动（失败抛异常但配置入库 + lastError）。
     */
    void saveOrUpdateProxy(StreamProxy proxy);

    /**
     * 启用：把配置推给 ZLM，回填 proxy_key / status。
     * <p>已经处于 status=1 的忽略。</p>
     */
    void enable(Long id);

    /**
     * 停用：从 ZLM 摘掉，清 proxy_key，status=0。
     */
    void disable(Long id);

    /**
     * 删除：内部先 disable 再物理删除。
     */
    void deleteById(Long id);

    /**
     * 批量删除
     */
    void deleteBatch(List<Long> ids);

    /**
     * 取播放地址（只有 status=1 时才有实际 URL）
     */
    StreamProxyPlayUrlResponse getPlayUrls(Long id);

    /**
     * 跟 ZLM 真实流列表对账：库里有但 ZLM 已经没这条流的，标 status=0、清 proxyKey、写 lastError。
     * 由 {@code StreamProxyReconciler} 定时调用 + {@code ZlmHookService.onServerStarted} 触发。
     */
    void reconcileWithZlm(String mediaServerId);
}
