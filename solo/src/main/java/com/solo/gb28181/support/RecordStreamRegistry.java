package com.solo.gb28181.support;

import com.solo.common.ServiceException;
import com.solo.gb28181.constant.GbRedisKeys;
import com.solo.gb28181.entity.GbInviteSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 录像归属独立存放，不跟 INVITE 会话一起删。
 * ZLM 关流和 on_record_mp4 是两个异步事件，尾片可能等会话删完才到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordStreamRegistry {

    /** 停止后的归属信息只留给尾片回调使用，Redis 到期自删，不需要按流挂延迟线程 */
    private static final Duration RETIRED_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 在录档案不过期；同名流重新开录时，SET 也会清掉上一段会话留下的 TTL。 */
    public void register(GbInviteSession session) {
        String streamId = session.getStreamId();
        try {
            redisTemplate.opsForValue().set(key(streamId), toJson(Context.from(session)));
        } catch (Exception e) {
            throw new ServiceException("录像流档案写入失败: " + e.getMessage());
        }
    }

    /** 停止时重写一份短 TTL 档案；即使开录时的档案缺失，也能保住最后的回调。 */
    public void retire(GbInviteSession session) {
        String streamId = session.getStreamId();
        try {
            redisTemplate.opsForValue().set(key(streamId), toJson(Context.from(session)), RETIRED_TTL);
        } catch (Exception e) {
            // Redis 抖动不能拦住 BYE/关流；最坏退化为旧行为，丢停止后的尾片
            log.warn("[云端录制] 终态档案写入失败 stream={} err={}", streamId, e.getMessage());
        }
    }

    public Context get(String streamId) {
        if (streamId == null || streamId.isBlank()) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(key(streamId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Context.class);
        } catch (Exception e) {
            log.warn("[云端录制] 录像流档案反序列化失败 stream={} err={}", streamId, e.getMessage());
            return null;
        }
    }

    private static String key(String streamId) {
        return GbRedisKeys.RECORD_STREAM + streamId;
    }

    private String toJson(Context context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new ServiceException("录像流档案序列化失败: " + e.getMessage());
        }
    }

    public record Context(String deviceId, String channelId, String serverId) {

        static Context from(GbInviteSession session) {
            return new Context(session.getDeviceId(), session.getChannelId(), session.getServerId());
        }
    }
}
