package com.solo.openapi.auth;

import com.solo.openapi.constant.OpenApiRedisKeys;
import com.solo.openapi.entity.OpenApp;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 基于 Redis 固定窗口的限流：秒级 QPS + 日调用量。
 * 计数在鉴权通过后才发生，失败请求不占配额。
 */
@Component
@RequiredArgsConstructor
public class OpenApiRateLimiter {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedisTemplate<String, Object> redisTemplate;

    /** true=放行，false=超过 QPS */
    public boolean tryAcquireQps(OpenApp app) {
        int limit = app.getQpsLimit() == null ? 0 : app.getQpsLimit();
        if (limit <= 0) {
            return true;
        }
        String key = OpenApiRedisKeys.RATE_QPS_PREFIX + app.getAppKey() + ":" + System.currentTimeMillis() / 1000;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(2));
        }
        return count == null || count <= limit;
    }

    /** true=未超日配额，false=已超限 */
    public boolean tryAcquireDaily(OpenApp app) {
        long limit = app.getDailyLimit() == null ? 0 : app.getDailyLimit();
        if (limit <= 0) {
            return true;
        }
        String key = OpenApiRedisKeys.RATE_DAY_PREFIX + app.getAppKey() + ":" + LocalDate.now().format(DAY_FMT);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
        return count == null || count <= limit;
    }
}
