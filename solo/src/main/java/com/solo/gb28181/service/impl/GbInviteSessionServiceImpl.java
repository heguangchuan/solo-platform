package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.service.GbInviteSessionService;
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
 * 点播会话 Redis 实现
 *
 * <h3>Key 布局（前缀 {@code gb28181:invite:}）</h3>
 * <ul>
 *   <li>{@code s:<streamId>}              主体 JSON（业务唯一标识，单点写入）</li>
 *   <li>{@code c:<callId>}      = streamId    INVITE Dialog Call-Id 反查</li>
 *   <li>{@code y:<ssrc>}        = streamId    SSRC 反查（备用）</li>
 *   <li>{@code d:<deviceId>:<channelId>} = streamId  通道反查（幂等检测）</li>
 *   <li>{@code all}             Set&lt;streamId&gt;   全集，启动清扫 + SsrcFactory 预占用</li>
 * </ul>
 *
 * <h3>一致性策略</h3>
 * <p>主体与索引在多 key 上，绝对原子很难做。我们的折中：</p>
 * <ol>
 *   <li>save：先写主体，再依次写索引 + 加入全集 — 中间失败的极端情况由读路径兜底</li>
 *   <li>update：仅覆盖主体（callId/ssrc 在 save 后不会变，无索引重写需要）</li>
 *   <li>removeByStreamId：先读主体取出 callId/ssrc/dev/chan，再依次删除所有相关 key</li>
 *   <li>读路径（getByCallId / getBySsrc）：先读索引拿 streamId 再读主体；主体为 null 视为索引脏，
 *       顺手删 stale 索引</li>
 * </ol>
 *
 * <h3>容量</h3>
 * <p>每条会话主体 ~1 KB；预期同时活跃数百级别即可，Redis 单实例够用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbInviteSessionServiceImpl implements GbInviteSessionService {

    private static final String PREFIX = "gb28181:invite:";
    private static final String K_SESSION = PREFIX + "s:";
    private static final String K_CALL    = PREFIX + "c:";
    private static final String K_SSRC    = PREFIX + "y:";
    private static final String K_DEVCHAN = PREFIX + "d:";
    private static final String K_ALL     = PREFIX + "all";

    private final StringRedisTemplate redisTemplate;

    /**
     * Jackson 3.x（{@code tools.jackson} 系列）默认集成 JavaTime 支持，
     * LocalDateTime 直接走 ISO-8601 字符串，不需要单独引 jsr310 模块。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void save(GbInviteSession s) {
        validateMainKeys(s);
        String streamId = s.getStreamId();
        String json = toJson(s);

        // 主体 + 反查索引 + 全集 Set；一次最多 5 个 key 直接顺序调用，不上 pipeline
        redisTemplate.opsForValue().set(K_SESSION + streamId, json);
        if (s.getCallId() != null) redisTemplate.opsForValue().set(K_CALL + s.getCallId(), streamId);
        if (s.getSsrc() != null)   redisTemplate.opsForValue().set(K_SSRC + s.getSsrc(), streamId);
        if (s.getDeviceId() != null && s.getChannelId() != null) {
            redisTemplate.opsForValue().set(K_DEVCHAN + s.getDeviceId() + ":" + s.getChannelId(), streamId);
        }
        redisTemplate.opsForSet().add(K_ALL, streamId);
    }

    @Override
    public void update(GbInviteSession s) {
        validateMainKeys(s);
        String streamId = s.getStreamId();
        // 当前业务里 update 仅覆盖主体字段；callId/ssrc 在 save 后不变，索引无需重写
        redisTemplate.opsForValue().set(K_SESSION + streamId, toJson(s));
    }

    @Override
    public void removeByStreamId(String streamId) {
        if (streamId == null || streamId.isBlank()) return;
        GbInviteSession s = getByStreamId(streamId);
        // 主体 + 全集 Set 是必删的；callId/ssrc/dev:chan 索引仅在主体存在时才有依据可删。
        // 一次 5 个 key，直接顺序调用即可；不必上 pipeline 走 SessionCallback 泛型那套。
        redisTemplate.delete(K_SESSION + streamId);
        redisTemplate.opsForSet().remove(K_ALL, streamId);
        if (s != null) {
            if (s.getCallId() != null) redisTemplate.delete(K_CALL + s.getCallId());
            if (s.getSsrc() != null)   redisTemplate.delete(K_SSRC + s.getSsrc());
            if (s.getDeviceId() != null && s.getChannelId() != null) {
                redisTemplate.delete(K_DEVCHAN + s.getDeviceId() + ":" + s.getChannelId());
            }
        }
    }

    @Override
    public GbInviteSession getByStreamId(String streamId) {
        if (streamId == null || streamId.isBlank()) return null;
        String json = redisTemplate.opsForValue().get(K_SESSION + streamId);
        return json == null ? null : fromJson(json);
    }

    @Override
    public GbInviteSession getByCallId(String callId) {
        return resolveByIndex(K_CALL + callId);
    }

    @Override
    public GbInviteSession getBySsrc(String ssrc) {
        return resolveByIndex(K_SSRC + ssrc);
    }

    @Override
    public List<GbInviteSession> listActiveByDeviceAndChannel(String deviceId, String channelId) {
        if (deviceId == null || channelId == null) return Collections.emptyList();
        String streamId = redisTemplate.opsForValue().get(K_DEVCHAN + deviceId + ":" + channelId);
        if (streamId == null) return Collections.emptyList();
        GbInviteSession s = getByStreamId(streamId);
        if (s == null) {
            // 索引脏（主体被删而索引漏删，理论极少见）
            redisTemplate.delete(K_DEVCHAN + deviceId + ":" + channelId);
            return Collections.emptyList();
        }
        return List.of(s);
    }

    @Override
    public List<GbInviteSession> listAllActive() {
        Set<String> streamIds = redisTemplate.opsForSet().members(K_ALL);
        if (streamIds == null || streamIds.isEmpty()) return Collections.emptyList();
        List<GbInviteSession> out = new ArrayList<>(streamIds.size());
        for (String id : streamIds) {
            GbInviteSession s = getByStreamId(id);
            if (s != null) {
                out.add(s);
            } else {
                // 全集里的 id 主体不存在，顺手清扫
                redisTemplate.opsForSet().remove(K_ALL, id);
            }
        }
        return out;
    }

    // ---------------- helpers ----------------

    private GbInviteSession resolveByIndex(String indexKey) {
        String streamId = redisTemplate.opsForValue().get(indexKey);
        if (streamId == null) return null;
        GbInviteSession s = getByStreamId(streamId);
        if (s == null) {
            // stale 索引：主体已删但索引漏删
            redisTemplate.delete(indexKey);
        }
        return s;
    }

    private static void validateMainKeys(GbInviteSession s) {
        if (s == null || s.getStreamId() == null || s.getStreamId().isBlank()) {
            throw new ServiceException("GbInviteSession.streamId 不能为空");
        }
    }

    private static String toJson(GbInviteSession s) {
        try {
            return MAPPER.writeValueAsString(s);
        } catch (Exception e) {
            throw new ServiceException("会话序列化失败: " + e.getMessage());
        }
    }

    private static GbInviteSession fromJson(String json) {
        try {
            return MAPPER.readValue(json, GbInviteSession.class);
        } catch (Exception e) {
            log.warn("[实时点播] 会话反序列化失败：{}", e.getMessage());
            return null;
        }
    }
}
