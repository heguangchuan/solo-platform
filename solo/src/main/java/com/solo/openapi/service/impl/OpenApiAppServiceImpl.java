package com.solo.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.openapi.request.OpenAppPageRequest;
import com.solo.openapi.request.OpenAppSaveRequest;
import com.solo.openapi.config.OpenApiProperties;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.constant.OpenApiRedisKeys;
import com.solo.openapi.entity.OpenApp;
import com.solo.openapi.mapper.OpenAppMapper;
import com.solo.openapi.service.OpenApiAppService;
import com.solo.openapi.support.OpenApiCipher;
import com.solo.openapi.support.OpenApiScopeCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiAppServiceImpl extends ServiceImpl<OpenAppMapper, OpenApp> implements OpenApiAppService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OpenApiCipher cipher;
    private final OpenApiProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OpenApiScopeCatalog scopeCatalog;

    @Override
    public CreatedSecret createApp(OpenAppSaveRequest req) {
        validateScopes(req.getScopes());
        String appKey = "ak_" + randomToken(18);
        String appSecret = randomToken(32);
        // 回调签名密钥单独生成，跟接口密钥互不影响
        String callbackSecret = randomToken(24);

        OpenApp app = new OpenApp();
        app.setAppName(req.getAppName());
        app.setScopes(req.getScopes() != null ? req.getScopes() : "");
        app.setIpWhitelist(req.getIpWhitelist());
        app.setCallbackUrl(req.getCallbackUrl());
        app.setEventSubscriptions(req.getEventSubscriptions() != null ? req.getEventSubscriptions() : "");
        app.setQpsLimit(req.getQpsLimit() != null ? req.getQpsLimit() : 10);
        app.setDailyLimit(req.getDailyLimit() != null ? req.getDailyLimit() : 0L);
        app.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        app.setExpireTime(req.getExpireTime());
        app.setRemark(req.getRemark());

        app.setAppKey(appKey);
        app.setSecretCipher(cipher.encrypt(appSecret));
        app.setCallbackSecretCipher(cipher.encrypt(callbackSecret));
        app.setOldSecretCipher(null);
        app.setOldSecretExpireTime(null);

        save(app);
        log.info("[开放API] 创建应用 appKey={} name={}", appKey, app.getAppName());
        return new CreatedSecret(appKey, appSecret, callbackSecret, app.getId());
    }

    @Override
    public CreatedSecret rotateSecret(Long id) {
        // 必须查全字段，普通查询拿不到 secretCipher
        OpenApp app = baseMapper.selectFullById(id);
        if (app == null) {
            throw new ServiceException("应用不存在");
        }
        String newSecret = randomToken(32);
        // 旧密钥保留一个过渡期，过渡期内仍可验签
        app.setOldSecretCipher(app.getSecretCipher());
        app.setOldSecretExpireTime(LocalDateTime.now().plusHours(properties.getOldSecretGraceHours()));
        app.setSecretCipher(cipher.encrypt(newSecret));
        updateById(app);
        evictCache(app.getAppKey());
        log.info("[开放API] 应用换钥 appKey={} 旧密钥过渡期{}小时", app.getAppKey(), properties.getOldSecretGraceHours());
        // 换的只是接口签名密钥，回调密钥不动，也不再明文返回
        return new CreatedSecret(app.getAppKey(), newSecret, null, id);
    }

    @Override
    public OpenApp getDetail(Long id) {
        OpenApp app = baseMapper.selectFullById(id);
        if (app == null) {
            throw new ServiceException("应用不存在");
        }
        return app;
    }

    @Override
    public void updateApp(OpenAppSaveRequest req) {
        if (req.getId() == null) {
            throw new ServiceException("应用ID不能为空");
        }
        validateScopes(req.getScopes());
        OpenApp exists = getById(req.getId());
        if (exists == null) {
            throw new ServiceException("应用不存在");
        }
        // 密钥不通过此接口修改，避免被覆盖
        OpenApp update = new OpenApp();
        update.setId(req.getId());
        update.setAppName(req.getAppName());
        update.setScopes(req.getScopes());
        update.setIpWhitelist(req.getIpWhitelist());
        update.setCallbackUrl(req.getCallbackUrl());
        update.setEventSubscriptions(req.getEventSubscriptions());
        update.setQpsLimit(req.getQpsLimit());
        update.setDailyLimit(req.getDailyLimit());
        update.setStatus(req.getStatus());
        update.setExpireTime(req.getExpireTime());
        update.setRemark(req.getRemark());
        updateById(update);
        evictCache(exists.getAppKey());
    }

    @Override
    public void enable(Long id) {
        changeStatus(id, 1);
    }

    @Override
    public void disable(Long id) {
        changeStatus(id, 0);
    }

    private void changeStatus(Long id, int status) {
        OpenApp app = getById(id);
        if (app == null) {
            throw new ServiceException("应用不存在");
        }
        OpenApp update = new OpenApp();
        update.setId(id);
        update.setStatus(status);
        updateById(update);
        evictCache(app.getAppKey());
    }

    @Override
    public Page<OpenApp> pageQuery(OpenAppPageRequest req) {
        LambdaQueryWrapper<OpenApp> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getAppName())) {
            wrapper.like(OpenApp::getAppName, req.getAppName());
        }
        if (req.getStatus() != null) {
            wrapper.eq(OpenApp::getStatus, req.getStatus());
        }
        wrapper.orderByDesc(OpenApp::getCreateTime);
        return page(new Page<>(req.getPage(), req.getPageSize()), wrapper);
    }

    @Override
    public AppSecret loadByAppKey(String appKey) {
        if (!StringUtils.hasText(appKey)) {
            return null;
        }
        String cacheKey = OpenApiRedisKeys.OPEN_APP_PREFIX + appKey;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof AppSecret appSecret) {
            return appSecret;
        }

        // 普通查询不返回 secretCipher，用自定义全字段查询
        OpenApp app = baseMapper.selectFullByAppKey(appKey);
        if (app == null) {
            return null;
        }
        AppSecret appSecret = new AppSecret(
                app,
                cipher.decrypt(app.getSecretCipher()),
                cipher.decrypt(app.getOldSecretCipher()));
        redisTemplate.opsForValue().set(cacheKey, appSecret,
                Duration.ofMinutes(OpenApiConstants.APP_CACHE_TTL_MINUTES));
        return appSecret;
    }

    @Override
    public void evictCache(String appKey) {
        if (StringUtils.hasText(appKey)) {
            redisTemplate.delete(OpenApiRedisKeys.OPEN_APP_PREFIX + appKey);
        }
    }

    @Override
    public Set<String> parseScopes(String scopes) {
        if (!StringUtils.hasText(scopes)) {
            return Set.of();
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private void validateScopes(String scopes) {
        Set<String> requested = parseScopes(scopes);
        if (requested.isEmpty()) {
            return;
        }
        Set<String> known = scopeCatalog.codes();
        List<String> unknown = requested.stream()
                .filter(code -> !known.contains(code))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new ServiceException("未知的授权范围: " + String.join(", ", unknown));
        }
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
