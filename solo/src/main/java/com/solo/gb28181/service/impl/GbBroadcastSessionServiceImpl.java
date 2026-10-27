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
import java.util.Objects;
import java.util.Set;

/**
 * 广播会话 Redis 存储。主体存 BROADCAST_SESSION + id，
 * 另建 callId、srcStream、全集三张反查索引。
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
        // 上级腿 callId 同样反查到本会话
        if (s.getParentCallId() != null) {
            redisTemplate.opsForValue().set(GbRedisKeys.BROADCAST_BY_CALL + s.getParentCallId(), id);
        }
        if (s.getSrcStream() != null) {
            redisTemplate.opsForSet().add(GbRedisKeys.BROADCAST_BY_SRC_STREAM + s.getSrcStream(), id);
        }
        redisTemplate.opsForSet().add(GbRedisKeys.BROADCAST_ALL_ACTIVE, id);
    }

    @Override
    public void update(GbBroadcastSession s) {
        validate(s);
        String id = s.getBroadcastStreamId();
        GbBroadcastSession old = get(id);
        redisTemplate.opsForValue().set(GbRedisKeys.BROADCAST_SESSION + id, toJson(s));
        // 索引字段变化时先删旧索引
        if (old != null) {
            if (!Objects.equals(old.getCallId(), s.getCallId()) && old.getCallId() != null) {
                redisTemplate.delete(GbRedisKeys.BROADCAST_BY_CALL + old.getCallId());
            }
            if (!Objects.equals(old.getParentCallId(), s.getParentCallId())
                    && old.getParentCallId() != null) {
                redisTemplate.delete(GbRedisKeys.BROADCAST_BY_CALL + old.getParentCallId());
            }
            if (!Objects.equals(old.getSrcStream(), s.getSrcStream()) && old.getSrcStream() != null) {
                redisTemplate.opsForSet()
                        .remove(GbRedisKeys.BROADCAST_BY_SRC_STREAM + old.getSrcStream(), id);
            }
        }
        if (s.getCallId() != null) {
            redisTemplate.opsForValue().set(GbRedisKeys.BROADCAST_BY_CALL + s.getCallId(), id);
        }
        if (s.getParentCallId() != null) {
            redisTemplate.opsForValue().set(GbRedisKeys.BROADCAST_BY_CALL + s.getParentCallId(), id);
        }
        if (s.getSrcStream() != null) {
            redisTemplate.opsForSet().add(GbRedisKeys.BROADCAST_BY_SRC_STREAM + s.getSrcStream(), id);
        }
        redisTemplate.opsForSet().add(GbRedisKeys.BROADCAST_ALL_ACTIVE, id);
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
            if (s.getParentCallId() != null) {
                redisTemplate.delete(GbRedisKeys.BROADCAST_BY_CALL + s.getParentCallId());
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
    public GbBroadcastSession getByParentCallId(String parentCallId) {
        return getByCallId(parentCallId);
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
        // 端口段内顺序回绕
        int offset = (int) (Math.floorMod(seq, (long) span));
        return start + offset;
    }


    private static void validate(GbBroadcastSession s) {
        if (s == null || s.getBroadcastStreamId() == null || s.getBroadcastStreamId().isBlank()) {
            throw new ServiceException("GbBroadcastSession.broadcastStreamId 不能为空");
        }
    }

    private String toJson(GbBroadcastSession s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (Exception e) {
            throw new ServiceException((s.isTalk() ? "语音对讲" : "语音播报") + "会话序列化失败: " + e.getMessage());
        }
    }

    private GbBroadcastSession fromJson(String json) {
        try {
            return objectMapper.readValue(json, GbBroadcastSession.class);
        } catch (Exception e) {
            log.warn("[语音会话] 会话反序列化失败：{}", e.getMessage());
            return null;
        }
    }
}
