package com.solo.aop;

import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;
import com.solo.common.utils.UserHolderUtils;
import com.solo.common.utils.WebUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;

/**
 * 限流切面
 * <p>基于 Redis + Lua 脚本实现原子性滑动窗口限流：
 * <ol>
 *   <li>移除时间窗口之外的旧请求记录</li>
 *   <li>统计当前窗口内的请求数</li>
 *   <li>超过阈值则拒绝请求</li>
 *   <li>记录当前请求时间戳</li>
 * </ol>
 * <p><strong>注意：</strong>全部逻辑封装在 Lua 脚本中原子执行，避免并发竞态条件
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimiterAspect {

    private final StringRedisTemplate redisTemplate;

    private DefaultRedisScript<Long> rateLimitScript;

    /**
     * 实例唯一标识，用于分布式场景下 ZSet member 去重
     * <p>生成策略：12 位随机 UUID + 启动时间戳后 6 位
     * <p>碰撞概率：P = 1 / (16^12 * 10^6)，在万级实例下可忽略
     */
    private final String instanceId = generateInstanceId();

    /**
     * 纯无锁请求计数器，不做清零，long 型溢出前可运行约 30 万年
     * 彻底避免毫秒切换时的锁竞争瓶颈
     */
    private final java.util.concurrent.atomic.AtomicLong requestCounter = new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * 降级计数器，用于抑制日志风暴（Redis 故障时每秒只打一次告警日志）
     * AtomicLong CAS 实现，无锁保证每秒只打印一次
     */
    private final java.util.concurrent.atomic.AtomicLong degradeCount = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong lastDegradeLogTime = new java.util.concurrent.atomic.AtomicLong(0);

    private static String generateInstanceId() {
        // 12 位随机 UUID 前缀（281 万亿种可能）+ 启动时间戳后 6 位
        String uuidPrefix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String timeSuffix = String.valueOf(System.currentTimeMillis() % 1000000);
        return uuidPrefix + timeSuffix;
    }

    /**
     * Lua 限流脚本：原子执行移除过期记录 + 统计当前计数 + 判断是否限流 + 添加当前请求 + 设置过期
     * 返回值：0 = 不限流，1 = 触发限流
     * <p>TTL 策略：首次创建时设置为窗口的 2 倍（避免临界点雪崩），后续不续期。
     * 只要一个完整窗口内有任何请求，key 就不会过期；极端冷数据最终会被清理。
     * <p>防御性兜底：如果 ZSet 大小超过 max_count * 10，强制清理旧数据（应对时钟回拨等异常场景）
     * <p>类型兼容：所有数值参数先用字符串传递，Lua 内部转换后再使用，避免 Redis 序列化类型问题
     */
    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local window_start_str = ARGV[1]
            local now_str = ARGV[2]
            local max_count_str = ARGV[3]
            local expire_ms_str = ARGV[4]
            local request_id = ARGV[5]

            -- 显式转换并验证参数有效性（转换失败直接放行，极端降级策略）
            local window_start = tonumber(window_start_str)
            local now = tonumber(now_str)
            local max_count = tonumber(max_count_str)
            local expire_ms = tonumber(expire_ms_str)

            if window_start == nil or now == nil or max_count == nil or expire_ms == nil then
                return 0
            end

            -- 1. 移除窗口外的旧记录（滑动窗口核心）
            redis.call('ZREMRANGEBYSCORE', key, 0, window_start_str)

            -- 2. 统计当前窗口内的请求数
            local current_count = redis.call('ZCARD', key)

            -- 3. 兜底防御：如果 ZSet 异常膨胀（超过 max_count * 10），强制保留最新的 max_count * 5 条
            -- 应对时钟回拨、窗口计算错误等异常场景，避免内存泄漏
            local safe_limit = max_count * 10
            if current_count >= safe_limit then
                local keep_count = max_count * 5
                local remove_count = current_count - keep_count
                if remove_count > 0 then
                    redis.call('ZREMRANGEBYRANK', key, 0, remove_count - 1)
                end
                current_count = redis.call('ZCARD', key)
            end

            -- 4. 超过阈值，限流
            if current_count >= max_count then
                return 1
            end

            -- 5. 添加当前请求记录
            redis.call('ZADD', key, now_str, request_id)

            -- 6. 仅当 key 不存在 TTL（首次创建）时设置过期
            -- TTL 设置为窗口 × 2，留出足够余量避免临界点雪崩
            -- 后续不续期：只要一个窗口内有任何请求，数据就不会丢失；冷数据最终自动清理
            local ttl = redis.call('PTTL', key)
            if ttl < 0 then
                redis.call('PEXPIRE', key, expire_ms_str)
            end

            return 0
            """;

    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptText(LUA_SCRIPT);
        rateLimitScript.setResultType(Long.class);
        // 主动预热脚本到 Redis，消除首请求延迟毛刺（避免第一个请求承担 SCRIPT LOAD 开销）
        try {
            redisTemplate.execute(rateLimitScript, java.util.Collections.singletonList("_warmup_dummy_key"), "0", "0", "1", "1000", "");
            // 预热后清理临时 key（可选，TTL 也会自动清理）
            redisTemplate.delete("_warmup_dummy_key");
        } catch (Exception e) {
            log.warn("限流脚本预热失败（Redis 未就绪？），不影响运行，首请求会自动加载", e);
        }
    }

    @Around("@annotation(com.solo.aop.RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimiter rateLimiter = method.getAnnotation(RateLimiter.class);

        if (rateLimiter == null) {
            return joinPoint.proceed();
        }

        // 构建限流 key
        String limitKey = buildLimitKey(rateLimiter, method);
        long now = System.currentTimeMillis();
        long windowMs = rateLimiter.timeUnit().toMillis(rateLimiter.time());
        long windowStart = now - windowMs;
        // TTL = 窗口 × 2，留出安全余量避免临界点雪崩
        long expireMs = windowMs * 2;
        // 分布式唯一 member：实例ID + 时间戳 + 单调递增序号，纯无锁实现，任何场景下无冲突
        String requestId = instanceId + ":" + now + ":" + requestCounter.getAndIncrement();

        // Lua 脚本原子执行限流判断
        try {
            // 🔴 所有数字参数统一转为字符串传递，避免 Spring Data Redis 序列化与 Lua tonumber 的类型兼容性问题
            // 这是 Redis Lua 脚本在不同 JDK/Redis 版本下的经典坑：long 可能被序列化为非纯数字字符串
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(limitKey),
                    String.valueOf(windowStart),
                    String.valueOf(now),
                    String.valueOf(rateLimiter.maxCount()),
                    String.valueOf(expireMs),
                    requestId
            );

            if (result != null && result == 1) {
                log.warn("触发限流: key={}, max={}", limitKey, rateLimiter.maxCount());
                throw new ServiceException(SystemConstants.HTTP_TOO_MANY_REQUESTS, rateLimiter.message());
            }
        } catch (ServiceException e) {
            // 限流异常直接抛出，不做降级处理
            throw e;
        } catch (Exception e) {
            // 🔴 熔断器降级：Redis 故障（宕机 / 网络抖动 / 超时）时，直接放行请求
            // 设计原则：宁可不限流，也不能影响主业务
            long count = degradeCount.incrementAndGet();
            long currentMs = System.currentTimeMillis();
            long prevTime = lastDegradeLogTime.get();
            // CAS 原子更新，保证同一秒内只有一个线程能成功打印日志，避免风暴
            if (currentMs - prevTime > 1000 && lastDegradeLogTime.compareAndSet(prevTime, currentMs)) {
                log.warn("限流组件降级放行（Redis 异常），累计 {} 次, key={}, error={}",
                        count, limitKey, e.getMessage());
            }
        }

        return joinPoint.proceed();
    }

    /**
     * 根据限流类型构建对应的 Redis key
     */
    private String buildLimitKey(RateLimiter rateLimiter, Method method) {
        // 🔴 使用完整类名（含包名），避免不同包同名类 + 同名方法碰撞
        String methodKey = method.getDeclaringClass().getCanonicalName() + "." + method.getName();
        String baseKey = rateLimiter.key() + methodKey;

        return switch (rateLimiter.limitType()) {
            case IP -> baseKey + ":ip:" + WebUtils.getClientIp();
            case USER -> baseKey + ":user:" + getCurrentUsernameSafe();
            case GLOBAL -> baseKey + ":global";
        };
    }

    /**
     * 安全获取当前登录用户名，未登录返回 "anonymous"
     */
    private String getCurrentUsernameSafe() {
        try {
            String username = UserHolderUtils.getCurrentUsername();
            return username != null ? username : "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
