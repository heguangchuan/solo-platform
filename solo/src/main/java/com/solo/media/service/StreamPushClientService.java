package com.solo.media.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.media.entity.StreamPushClient;
import com.solo.media.request.StreamPushClientPageRequest;

/**
 * 推流方鉴权服务
 */
public interface StreamPushClientService extends IService<StreamPushClient> {

    Page<StreamPushClient> pageQuery(StreamPushClientPageRequest req);

    /**
     * 新增推流方：clientId 用户填，token 后端自动生成 256 位随机串。
     */
    StreamPushClient createClient(StreamPushClient input);

    /**
     * 更新推流方：仅允许改 enabled / remark；clientId 与 token 不可变。
     * <p>要换 token 请用 {@link #resetToken(Long)}。</p>
     */
    void updateClient(StreamPushClient input);

    /**
     * 重置 token（安全事件时用）：生成新 token 覆盖原 token 字段。
     * <p>老 token 立即失效——但已建立的推流连接不受影响，需管理员在推流管理页手动中止。</p>
     */
    StreamPushClient resetToken(Long id);

    /**
     * 按 token 校验并返回 client_id：命中且 enabled=1 返回 client_id，其他情况返回 null。
     * <p>供 {@code ZlmHookService.onPublish} 使用。</p>
     */
    String authenticate(String token);

    /**
     * 按 clientId 精确查一次（用于回填 stream_push.client_id 时反查）；未命中返回 null。
     */
    String clientIdOfToken(String token);
}
