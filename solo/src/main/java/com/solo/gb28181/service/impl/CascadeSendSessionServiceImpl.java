package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.constant.GbRedisKeys;
import com.solo.gb28181.entity.CascadeSendSession;
import com.solo.gb28181.service.CascadeSendSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 级联推流会话 Redis 实现（仿 {@link GbInviteSessionServiceImpl}）。
 *
 * <h3>Key 布局（前缀 {@link GbRedisKeys#CASCADE_SEND_PREFIX}）</h3>
 * <ul>
 *   <li>{@link GbRedisKeys#CASCADE_SEND_SESSION}{sendStreamId}  主体 JSON</li>
 *   <li>{@link GbRedisKeys#CASCADE_SEND_BY_CALL}{callId} = sendStreamId  上级 INVITE Call-Id 反查（ACK/BYE 用）</li>
 *   <li>{@link GbRedisKeys#CASCADE_SEND_BY_SRC_STREAM}{srcStream} = Set&lt;sendStreamId&gt;  源流反查（一路流被多上级点播）</li>
 *   <li>{@link GbRedisKeys#CASCADE_SEND_ALL_ACTIVE}  全集 Set</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CascadeSendSessionServiceImpl implements CascadeSendSessionService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(CascadeSendSession s) {
        validate(s);
        String id = s.getSendStreamId();
        redisTemplate.opsForValue().set(GbRedisKeys.CASCADE_SEND_SESSION + id, toJson(s));
        if (s.getCallId() != null) {
            redisTemplate.opsForValue().set(GbRedisKeys.CASCADE_SEND_BY_CALL + s.getCallId(), id);
        }
        if (s.getSrcStream() != null) {
            redisTemplate.opsForSet().add(GbRedisKeys.CASCADE_SEND_BY_SRC_STREAM + s.getSrcStream(), id);
        }
        redisTemplate.opsForSet().add(GbRedisKeys.CASCADE_SEND_ALL_ACTIVE, id);
    }

    @Override
    public void update(CascadeSendSession s) {
        validate(s);
        redisTemplate.opsForValue().set(GbRedisKeys.CASCADE_SEND_SESSION + s.getSendStreamId(), toJson(s));
    }

    @Override
    public void removeBySendStreamId(String sendStreamId) {
        if (sendStreamId == null || sendStreamId.isBlank()) return;
        CascadeSendSession s = getBySendStreamId(sendStreamId);
        redisTemplate.delete(GbRedisKeys.CASCADE_SEND_SESSION + sendStreamId);
        redisTemplate.opsForSet().remove(GbRedisKeys.CASCADE_SEND_ALL_ACTIVE, sendStreamId);
        if (s != null) {
            if (s.getCallId() != null) {
                redisTemplate.delete(GbRedisKeys.CASCADE_SEND_BY_CALL + s.getCallId());
            }
            if (s.getSrcStream() != null) {
                redisTemplate.opsForSet().remove(GbRedisKeys.CASCADE_SEND_BY_SRC_STREAM + s.getSrcStream(), sendStreamId);
            }
        }
    }

    @Override
    public CascadeSendSession getBySendStreamId(String sendStreamId) {
        if (sendStreamId == null || sendStreamId.isBlank()) return null;
        String json = redisTemplate.opsForValue().get(GbRedisKeys.CASCADE_SEND_SESSION + sendStreamId);
        return json == null ? null : fromJson(json);
    }

    @Override
    public CascadeSendSession getByCallId(String callId) {
        if (callId == null) return null;
        String id = redisTemplate.opsForValue().get(GbRedisKeys.CASCADE_SEND_BY_CALL + callId);
        if (id == null) return null;
        CascadeSendSession s = getBySendStreamId(id);
        if (s == null) {
            redisTemplate.delete(GbRedisKeys.CASCADE_SEND_BY_CALL + callId);
        }
        return s;
    }

    @Override
    public List<CascadeSendSession> listBySrcStream(String srcStream) {
        if (srcStream == null) return Collections.emptyList();
        String idxKey = GbRedisKeys.CASCADE_SEND_BY_SRC_STREAM + srcStream;
        Set<String> ids = redisTemplate.opsForSet().members(idxKey);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<CascadeSendSession> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            CascadeSendSession s = getBySendStreamId(id);
            if (s != null) {
                out.add(s);
            } else {
                redisTemplate.opsForSet().remove(idxKey, id);
            }
        }
        return out;
    }

    @Override
    public List<CascadeSendSession> listAllActive() {
        Set<String> ids = redisTemplate.opsForSet().members(GbRedisKeys.CASCADE_SEND_ALL_ACTIVE);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<CascadeSendSession> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            CascadeSendSession s = getBySendStreamId(id);
            if (s != null) {
                out.add(s);
            } else {
                redisTemplate.opsForSet().remove(GbRedisKeys.CASCADE_SEND_ALL_ACTIVE, id);
            }
        }
        return out;
    }

    @Override
    public int acquireSendPort(int start, int end) {
        if (start <= 0 || end < start) {
            throw new ServiceException("非法发送端口段: " + start + "-" + end);
        }
        int span = end - start + 1;
        long seq = redisTemplate.opsForValue().increment(GbRedisKeys.CASCADE_SEND_PORT_SEQ, 1);
        // 回绕到端口段内。ZLM 若报端口占用，调用方可重试拿下一个。
        int offset = (int) (Math.floorMod(seq, (long) span));
        return start + offset;
    }

    // ---------------- helpers ----------------

    private static void validate(CascadeSendSession s) {
        if (s == null || s.getSendStreamId() == null || s.getSendStreamId().isBlank()) {
            throw new ServiceException("CascadeSendSession.sendStreamId 不能为空");
        }
    }

    private String toJson(CascadeSendSession s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (Exception e) {
            throw new ServiceException("级联推流会话序列化失败: " + e.getMessage());
        }
    }

    private CascadeSendSession fromJson(String json) {
        try {
            return objectMapper.readValue(json, CascadeSendSession.class);
        } catch (Exception e) {
            log.warn("[级联点播] 推流会话反序列化失败：{}", e.getMessage());
            return null;
        }
    }
}
