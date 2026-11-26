package com.solo.openapi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.openapi.request.OpenAppPageRequest;
import com.solo.openapi.request.OpenAppSaveRequest;
import com.solo.openapi.entity.OpenApp;

import java.util.Set;

public interface OpenApiAppService extends IService<OpenApp> {

    CreatedSecret createApp(OpenAppSaveRequest req);

    CreatedSecret rotateSecret(Long id);

    void updateApp(OpenAppSaveRequest req);

    void enable(Long id);

    void disable(Long id);

    Page<OpenApp> pageQuery(OpenAppPageRequest req);

    AppSecret loadByAppKey(String appKey);

    void evictCache(String appKey);

    Set<String> parseScopes(String scopes);

    OpenApp getDetail(Long id);

    // 明文密钥只在创建/换钥时返回这一次。
    record CreatedSecret(String appKey, String appSecret, String callbackSecret, Long id) {
    }

    record AppSecret(OpenApp app, String secret, String oldSecret) implements java.io.Serializable {
    }
}
