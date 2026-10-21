package com.solo.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.media.entity.StreamPushClient;
import com.solo.media.mapper.StreamPushClientMapper;
import com.solo.media.request.StreamPushClientPageRequest;
import com.solo.media.service.StreamPushClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamPushClientServiceImpl extends ServiceImpl<StreamPushClientMapper, StreamPushClient>
        implements StreamPushClientService {

    @Override
    public Page<StreamPushClient> pageQuery(StreamPushClientPageRequest req) {
        LambdaQueryWrapper<StreamPushClient> w = new LambdaQueryWrapper<>();
        if (req.getClientId() != null && !req.getClientId().isBlank()) {
            w.like(StreamPushClient::getClientId, req.getClientId());
        }
        if (req.getEnabled() != null) {
            w.eq(StreamPushClient::getEnabled, req.getEnabled());
        }
        w.orderByDesc(StreamPushClient::getUpdateTime);
        return page(new Page<>(req.getPage(), req.getPageSize()), w);
    }

    @Override
    public StreamPushClient createClient(StreamPushClient input) {
        if (input.getClientId() == null || input.getClientId().isBlank()) {
            throw new ServiceException("clientId 不能为空");
        }
        String clientId = input.getClientId().trim();
        boolean exists = lambdaQuery().eq(StreamPushClient::getClientId, clientId).exists();
        if (exists) {
            throw new ServiceException("clientId 已存在：" + clientId);
        }
        StreamPushClient row = new StreamPushClient();
        row.setClientId(clientId);
        row.setToken(newToken());
        row.setEnabled(input.getEnabled() == null ? 1 : input.getEnabled());
        row.setRemark(input.getRemark());
        save(row);
        log.info("[推流鉴权] 新增 clientId={} tokenPrefix={}", clientId, tokenPrefix(row.getToken()));
        return row;
    }

    @Override
    public void updateClient(StreamPushClient input) {
        if (input.getId() == null) {
            throw new ServiceException("id 不能为空");
        }
        StreamPushClient old = getById(input.getId());
        if (old == null) {
            throw new ServiceException("推流方不存在: " + input.getId());
        }
        // 仅允许更新 enabled / remark
        old.setEnabled(input.getEnabled() == null ? old.getEnabled() : input.getEnabled());
        old.setRemark(input.getRemark());
        updateById(old);
        log.info("[推流鉴权] 更新 id={} clientId={} enabled={}", old.getId(), old.getClientId(), old.getEnabled());
    }

    @Override
    public StreamPushClient resetToken(Long id) {
        StreamPushClient old = getById(id);
        if (old == null) {
            throw new ServiceException("推流方不存在: " + id);
        }
        old.setToken(newToken());
        updateById(old);
        log.warn("[推流鉴权] 重置 token clientId={} tokenPrefix={}", old.getClientId(), tokenPrefix(old.getToken()));
        return old;
    }

    @Override
    public String authenticate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        StreamPushClient hit = lambdaQuery()
                .eq(StreamPushClient::getToken, token)
                .eq(StreamPushClient::getEnabled, 1)
                .one();
        return hit == null ? null : hit.getClientId();
    }

    @Override
    public String clientIdOfToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        StreamPushClient hit = lambdaQuery().eq(StreamPushClient::getToken, token).one();
        return hit == null ? null : hit.getClientId();
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String tokenPrefix(String token) {
        if (token == null || token.length() < 6) {
            return "***";
        }
        return token.substring(0, 6) + "***";
    }
}
