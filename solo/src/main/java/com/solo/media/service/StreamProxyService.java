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
 */
public interface StreamProxyService extends IService<StreamProxy> {

    Page<StreamProxy> pageQuery(StreamProxyPageRequest request);

    /**
     * 新增或更新代理配置。
     * <p>若 {@code enabled=1}，落库后立即调 ZLM 推流；已在推的先 delStreamProxy 再重推。</p>
     */
    void saveOrUpdateProxy(StreamProxy proxy);

    /**
     * 启用：把配置推给 ZLM，回填 proxy_key / status。
     * <p>已经处于 status=1 的忽略。</p>
     */
    void enable(Long id);

    /**
     * 停用：从 ZLM 摘掉，清 proxy_key，status=0；enabled 一起置 0。
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
}
