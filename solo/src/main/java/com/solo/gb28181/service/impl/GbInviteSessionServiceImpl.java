package com.solo.gb28181.service.impl;

import com.solo.common.ServiceException;
import com.solo.gb28181.constant.GbRedisKeys;
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
 * <h3>Key 布局（前缀 {@link GbRedisKeys#INVITE_PREFIX}）</h3>
 * <ul>
 *   <li>{@link GbRedisKeys#INVITE_SESSION}{streamId}  主体 JSON（业务唯一标识，单点写入）</li>
 *   <li>{@link GbRedisKeys#INVITE_BY_CALL}{callId}    = streamId    INVITE Dialog Call-Id 反查</li>
 *   <li>{@link GbRedisKeys#INVITE_BY_SSRC}{ssrc}    = streamId    SSRC 反查（备用）</li>
 *   <li>{@link GbRedisKeys#INVITE_BY_DEVICE_CHANNEL}{deviceId}:{channelId} = Set&lt;streamId&gt;  通道反查（幂等检测）
 *       —— 同一通道可并存多路流（不同码流号各一路），故用 Set 而非单值</li>
 *   <li>{@link GbRedisKeys#INVITE_ALL_ACTIVE}       Set&lt;streamId&gt;   全集，启动清扫 + SsrcFactory 预占用</li>
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

    /** Jackson 3.x（{@code tools.jackson} 系列）默认集成 JavaTime 支持，
     * LocalDateTime 直接走 ISO-8601 字符串，不需要单独引 jsr310 模块。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(GbInviteSession s) {
        validateMainKeys(s);
        String streamId = s.getStreamId();
        String json = toJson(s);

        // 主体 + 反查索引 + 全集 Set；一次最多 5 个 key 直接顺序调用，不上 pipeline
        redisTemplate.opsForValue().set(GbRedisKeys.INVITE_SESSION + streamId, json);
        if (s.getCallId() != null) redisTemplate.opsForValue().set(GbRedisKeys.INVITE_BY_CALL + s.getCallId(), streamId);
        if (s.getSsrc() != null)   redisTemplate.opsForValue().set(GbRedisKeys.INVITE_BY_SSRC + s.getSsrc(), streamId);
        if (s.getDeviceId() != null && s.getChannelId() != null) {
            redisTemplate.opsForSet().add(GbRedisKeys.INVITE_BY_DEVICE_CHANNEL + s.getDeviceId() + ":" + s.getChannelId(), streamId);
        }
        redisTemplate.opsForSet().add(GbRedisKeys.INVITE_ALL_ACTIVE, streamId);
    }

    @Override
    public void update(GbInviteSession s) {
        validateMainKeys(s);
        String streamId = s.getStreamId();
        // 当前业务里 update 仅覆盖主体字段；callId/ssrc 在 save 后不变，索引无需重写
        redisTemplate.opsForValue().set(GbRedisKeys.INVITE_SESSION + streamId, toJson(s));
    }

    @Override
    public void removeByStreamId(String streamId) {
        if (streamId == null || streamId.isBlank()) return;
        GbInviteSession s = getByStreamId(streamId);
        // 主体 + 全集 Set 是必删的；callId/ssrc/dev:chan 索引仅在主体存在时才有依据可删。
        // 一次 5 个 key，直接顺序调用即可；不必上 pipeline 走 SessionCallback 泛型那套。
        redisTemplate.delete(GbRedisKeys.INVITE_SESSION + streamId);
        redisTemplate.opsForSet().remove(GbRedisKeys.INVITE_ALL_ACTIVE, streamId);
        if (s != null) {
            if (s.getCallId() != null) redisTemplate.delete(GbRedisKeys.INVITE_BY_CALL + s.getCallId());
            if (s.getSsrc() != null)   redisTemplate.delete(GbRedisKeys.INVITE_BY_SSRC + s.getSsrc());
            if (s.getDeviceId() != null && s.getChannelId() != null) {
                redisTemplate.opsForSet().remove(GbRedisKeys.INVITE_BY_DEVICE_CHANNEL + s.getDeviceId() + ":" + s.getChannelId(), streamId);
            }
        }
    }

    @Override
    public GbInviteSession getByStreamId(String streamId) {
        if (streamId == null || streamId.isBlank()) return null;
        String json = redisTemplate.opsForValue().get(GbRedisKeys.INVITE_SESSION + streamId);
        return json == null ? null : fromJson(json);
    }

    @Override
    public GbInviteSession getByCallId(String callId) {
        return resolveByIndex(GbRedisKeys.INVITE_BY_CALL + callId);
    }

    @Override
    public GbInviteSession getBySsrc(String ssrc) {
        return resolveByIndex(GbRedisKeys.INVITE_BY_SSRC + ssrc);
    }

    @Override
    public List<GbInviteSession> listActiveByDeviceAndChannel(String deviceId, String channelId) {
        if (deviceId == null || channelId == null) return Collections.emptyList();
        String idxKey = GbRedisKeys.INVITE_BY_DEVICE_CHANNEL + deviceId + ":" + channelId;
        Set<String> streamIds = redisTemplate.opsForSet().members(idxKey);
        if (streamIds == null || streamIds.isEmpty()) return Collections.emptyList();
        List<GbInviteSession> out = new ArrayList<>(streamIds.size());
        for (String streamId : streamIds) {
            GbInviteSession s = getByStreamId(streamId);
            if (s != null) {
                out.add(s);
            } else {
                // 索引脏（主体被删而索引漏删，理论极少见）：顺手从 Set 摘掉
                redisTemplate.opsForSet().remove(idxKey, streamId);
            }
        }
        return out;
    }

    @Override
    public List<GbInviteSession> listAllActive() {
        Set<String> streamIds = redisTemplate.opsForSet().members(GbRedisKeys.INVITE_ALL_ACTIVE);
        if (streamIds == null || streamIds.isEmpty()) return Collections.emptyList();
        List<GbInviteSession> out = new ArrayList<>(streamIds.size());
        for (String id : streamIds) {
            GbInviteSession s = getByStreamId(id);
            if (s != null) {
                out.add(s);
            } else {
                // 全集里的 id 主体不存在，顺手清扫
                redisTemplate.opsForSet().remove(GbRedisKeys.INVITE_ALL_ACTIVE, id);
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
