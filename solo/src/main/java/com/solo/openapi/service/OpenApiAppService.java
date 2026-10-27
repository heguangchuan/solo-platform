package com.solo.openapi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.openapi.request.OpenAppPageRequest;
import com.solo.openapi.request.OpenAppSaveRequest;
import com.solo.openapi.entity.OpenApp;

import java.util.Set;

/**
 * 开放 API 应用管理。管理端用维护类方法，鉴权层用 {@link #loadByAppKey}。
 */
public interface OpenApiAppService extends IService<OpenApp> {

    /**
     * 创建应用，返回明文密钥（仅此一次）。
     */
    CreatedSecret createApp(OpenAppSaveRequest req);

    /**
     * 换钥：旧密钥进入过渡期，返回新明文密钥。
     */
    CreatedSecret rotateSecret(Long id);

    /** 更新应用资料（名称、scope、白名单、回调、配额、状态） */
    void updateApp(OpenAppSaveRequest req);

    void enable(Long id);

    void disable(Long id);

    Page<OpenApp> pageQuery(OpenAppPageRequest req);

    /**
     * 鉴权/回调取档案（带 Redis 缓存），含解密后的当前密钥与旧密钥。
     */
    AppSecret loadByAppKey(String appKey);

    /** 应用变更后主动失效档案缓存 */
    void evictCache(String appKey);

    /** 解析 scope 串为集合 */
    Set<String> parseScopes(String scopes);

    /** 应用详情（含密钥密文，仅管理端后台用，不直接出参） */
    OpenApp getDetail(Long id);

    /** 创建/换钥时一次性返回的明文凭证 */
    record CreatedSecret(String appKey, String appSecret, String callbackSecret, Long id) {
    }

    /** 鉴权用档案：原始密钥 + 应用实体（缓存对象，需可序列化） */
    record AppSecret(OpenApp app, String secret, String oldSecret) implements java.io.Serializable {
    }
}
