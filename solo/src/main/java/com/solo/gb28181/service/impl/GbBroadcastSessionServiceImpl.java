package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.constant.GbRedisKeys;
import com.solo.gb28181.entity.GbBroadcastSession;
import com.solo.gb28181.service.GbBroadcastSessionService;
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
 * <h3>Key 布局（前缀 {@link GbRedisKeys#BROADCAST_PREFIX}）</h3>
 * <ul>
 *   <li>{@link GbRedisKeys#BROADCAST_SESSION}{broadcastStreamId}  主体 JSON</li>
 *   <li>{@link GbRedisKeys#BROADCAST_BY_CALL}{callId} = broadcastStreamId  设备 INVITE Call-Id 反查（ACK/BYE 用）</li>
 *   <li>{@link GbRedisKeys#BROADCAST_BY_SRC_STREAM}{srcStream} = Set&lt;broadcastStreamId&gt;  源流反查</li>
 *   <li>{@link GbRedisKeys#BROADCAST_ALL_ACTIVE}  全集 Set</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbBroadcastSessionServiceImpl implements GbBroadcastSessionService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(GbBroadcastSession s) {
        validate(s);
        String id = s.getBroadcastStreamId();
        redisTemplate.opsForValue().set(GbRedisKeys.BROADCAST_SESSION + id, toJson(s));
        if (s.getCallId() != null) {
            redisTemplate.opsForValue().set(GbRedisKeys.BROADCAST_BY_CALL + s.getCallId(), id);
        }
        if (s.getSrcStream() != null) {
            redisTemplate.opsForSet().add(GbRedisKeys.BROADCAST_BY_SRC_STREAM + s.getSrcStream(), id);
        }
        redisTemplate.opsForSet().add(GbRedisKeys.BROADCAST_ALL_ACTIVE, id);
    }

    @Override
    public void update(GbBroadcastSession s) {
        validate(s);
        redisTemplate.opsForValue().set(GbRedisKeys.BROADCAST_SESSION + s.getBroadcastStreamId(), toJson(s));
    }

    @Override
    public void remove(String broadcastStreamId) {
        if (broadcastStreamId == null || broadcastStreamId.isBlank()) return;
        GbBroadcastSession s = get(broadcastStreamId);
        redisTemplate.delete(GbRedisKeys.BROADCAST_SESSION + broadcastStreamId);
        redisTemplate.opsForSet().remove(GbRedisKeys.BROADCAST_ALL_ACTIVE, broadcastStreamId);
        if (s != null) {
            if (s.getCallId() != null) {
                redisTemplate.delete(GbRedisKeys.BROADCAST_BY_CALL + s.getCallId());
            }
            if (s.getSrcStream() != null) {
                redisTemplate.opsForSet().remove(GbRedisKeys.BROADCAST_BY_SRC_STREAM + s.getSrcStream(), broadcastStreamId);
            }
        }
    }

    @Override
    public GbBroadcastSession get(String broadcastStreamId) {
        if (broadcastStreamId == null || broadcastStreamId.isBlank()) return null;
        String json = redisTemplate.opsForValue().get(GbRedisKeys.BROADCAST_SESSION + broadcastStreamId);
        return json == null ? null : fromJson(json);
    }

    @Override
    public GbBroadcastSession getByCallId(String callId) {
        if (callId == null) return null;
        String id = redisTemplate.opsForValue().get(GbRedisKeys.BROADCAST_BY_CALL + callId);
        if (id == null) return null;
        GbBroadcastSession s = get(id);
        if (s == null) {
            redisTemplate.delete(GbRedisKeys.BROADCAST_BY_CALL + callId);
        }
        return s;
    }

    @Override
    public List<GbBroadcastSession> listBySrcStream(String srcStream) {
        if (srcStream == null) return Collections.emptyList();
        String idxKey = GbRedisKeys.BROADCAST_BY_SRC_STREAM + srcStream;
        Set<String> ids = redisTemplate.opsForSet().members(idxKey);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<GbBroadcastSession> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            GbBroadcastSession s = get(id);
            if (s != null) {
                out.add(s);
            } else {
                redisTemplate.opsForSet().remove(idxKey, id);
            }
        }
        return out;
    }

    @Override
    public List<GbBroadcastSession> listAllActive() {
        Set<String> ids = redisTemplate.opsForSet().members(GbRedisKeys.BROADCAST_ALL_ACTIVE);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<GbBroadcastSession> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            GbBroadcastSession s = get(id);
            if (s != null) {
                out.add(s);
            } else {
                redisTemplate.opsForSet().remove(GbRedisKeys.BROADCAST_ALL_ACTIVE, id);
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
        long seq = redisTemplate.opsForValue().increment(GbRedisKeys.BROADCAST_PORT_SEQ, 1);
        // 回绕到端口段内。ZLM 若报端口占用，调用方可重试拿下一个。
        int offset = (int) (Math.floorMod(seq, (long) span));
        return start + offset;
    }

    // ---------------- helpers ----------------

    private static void validate(GbBroadcastSession s) {
        if (s == null || s.getBroadcastStreamId() == null || s.getBroadcastStreamId().isBlank()) {
            throw new ServiceException("GbBroadcastSession.broadcastStreamId 不能为空");
        }
    }

    private String toJson(GbBroadcastSession s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (Exception e) {
            throw new ServiceException("语音广播会话序列化失败: " + e.getMessage());
        }
    }

    private GbBroadcastSession fromJson(String json) {
        try {
            return objectMapper.readValue(json, GbBroadcastSession.class);
        } catch (Exception e) {
            log.warn("[语音广播] 会话反序列化失败：{}", e.getMessage());
            return null;
        }
    }
}
