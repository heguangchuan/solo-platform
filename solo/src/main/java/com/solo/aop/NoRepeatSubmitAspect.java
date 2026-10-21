package com.solo.aop;

import com.solo.common.ServiceException;
import com.solo.common.utils.UserHolderUtils;
import com.solo.common.utils.WebUtils;
import com.solo.system.constant.SystemConstants;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Redis 分布式锁实现，SETNX + PX 原子命令保证并发安全
 * <p>故障降级：Redis 不可用时直接放行，不影响主业务
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class NoRepeatSubmitAspect {

    private static final String LOCK_KEY_PREFIX = "repeat_lock:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Lua 脚本：原子性校验并释放锁
     * 只有锁持有者才能释放锁，避免误删其他请求的锁
     * 返回 1 = 释放成功，0 = 不是自己的锁（已过期或被他人持有）
     */
    private static final String UNLOCK_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """;

    private DefaultRedisScript<Long> unlockScript;

    /**
     * 降级计数器，用于抑制日志风暴（Redis 故障时每秒只打一次告警日志）
     */
    private final AtomicLong degradeCount = new AtomicLong(0);
    private final AtomicLong lastDegradeLogTime = new AtomicLong(0);

    @jakarta.annotation.PostConstruct
    public void init() {
        unlockScript = new DefaultRedisScript<>();
        unlockScript.setScriptText(UNLOCK_SCRIPT);
        unlockScript.setResultType(Long.class);
    }

    @Around("@annotation(noRepeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, NoRepeatSubmit noRepeatSubmit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 构建锁 key
        String lockKey = buildLockKey(noRepeatSubmit, method, joinPoint.getArgs());
        // 锁持有者唯一标识：随机 UUID，防止业务超时后误删其他请求的锁
        // 经典分布式锁问题：请求A锁过期了，请求B拿到锁，请求A执行完误删请求B的锁
        String lockHolder = java.util.UUID.randomUUID().toString().replace("-", "");

        boolean locked = false;
        try {
            // 原子加锁：SET key value NX PX expire，value 是当前请求的唯一标识
            // 成功返回 true，锁已存在返回 false
            Duration lockDuration = Duration.ofMillis(noRepeatSubmit.timeUnit().toMillis(noRepeatSubmit.expire()));
            locked = Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockHolder, lockDuration));

            if (!locked) {
                log.warn("触发防重复提交: key={}", lockKey);
                throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, noRepeatSubmit.message());
            }

            // 加锁成功，执行业务
            return joinPoint.proceed();

        } catch (ServiceException e) {
            // 防重异常直接抛出
            throw e;
        } catch (Exception e) {
            // 熔断器降级：Redis 故障时直接放行，不影响主业务
            long count = degradeCount.incrementAndGet();
            long currentMs = System.currentTimeMillis();
            long prevTime = lastDegradeLogTime.get();
            // CAS 原子更新，保证同一秒内只有一个线程能成功打印日志，避免风暴
            if (currentMs - prevTime > 1000 && lastDegradeLogTime.compareAndSet(prevTime, currentMs)) {
                log.warn("防重复提交组件降级放行（Redis 异常），累计 {} 次, key={}, error={}",
                        count, lockKey, e.getMessage());
            }
            return joinPoint.proceed();
        } finally {
            // 配置了执行完成立即解锁，且确实拿到了锁
            if (locked && noRepeatSubmit.unlockWhenFinish()) {
                try {
                    // 原子校验并释放锁：只有锁的持有者才能释放，防止误删其他请求的锁
                    redisTemplate.execute(unlockScript, Collections.singletonList(lockKey), lockHolder);
                } catch (Exception e) {
                    log.warn("释放防重锁失败（不影响业务，等待过期自动释放）: key={}", lockKey);
                }
            }
        }
    }

    private String buildLockKey(NoRepeatSubmit noRepeatSubmit, Method method, Object[] args) {
        // 使用完整类名（含包名），避免不同包同名类 + 同名方法碰撞
        String methodKey = method.getDeclaringClass().getCanonicalName() + "." + method.getName();
        String baseKey = LOCK_KEY_PREFIX + methodKey;

        // 可选：将参数 hash 纳入锁 key（参数级防重）
        if (noRepeatSubmit.includeParams() && args != null && args.length > 0) {
            long paramsHash = argsHash(args);
            // 转成无符号十六进制字符串，比数字更短且阅读友好
            baseKey = baseKey + ":h:" + Long.toHexString(paramsHash);
        }

        final String finalBaseKey = baseKey;
        return switch (noRepeatSubmit.lockType()) {
            case USER -> {
                String username = getCurrentUsernameSafe();
                yield finalBaseKey + ":u:" + username;
            }
            case IP -> finalBaseKey + ":ip:" + WebUtils.getClientIp();
            case GLOBAL -> finalBaseKey + ":global";
        };
    }

    /**
     * 分层累积哈希，碰撞概率远低于单个 hashCode；纯内存计算，无序列化开销，适合高并发场景
     */
    private long argsHash(Object[] args) {
        // 使用 long (64位) 替代 int (32位)，碰撞概率从 2^31 降到 2^63
        long result = 1;
        for (Object arg : args) {
            result = 31 * result + singleObjHash(arg);
        }
        return result;
    }

    private long singleObjHash(Object arg) {
        if (arg == null) {
            return 0;
        }

        // 不可序列化的请求/响应/二进制对象，不参与 hash
        if (arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof MultipartFile
                || arg instanceof MultipartFile[]) {
            return 0;
        }

        // 数组：递归每个元素（关键！避免 Object[].hashCode() 只返回对象标识）
        if (arg.getClass().isArray()) {
            if (arg instanceof Object[] arr) {
                long hash = 1;
                for (Object e : arr) {
                    hash = 31 * hash + singleObjHash(e);
                }
                return hash;
            } else if (arg instanceof byte[] arr) {
                return Arrays.hashCode(arr);
            } else if (arg instanceof int[] arr) {
                return Arrays.hashCode(arr);
            } else if (arg instanceof long[] arr) {
                return Arrays.hashCode(arr);
            } else if (arg instanceof boolean[] arr) {
                return Arrays.hashCode(arr);
            } else if (arg instanceof char[] arr) {
                return Arrays.hashCode(arr);
            } else if (arg instanceof double[] arr) {
                return Arrays.hashCode(arr);
            } else if (arg instanceof float[] arr) {
                return Arrays.hashCode(arr);
            } else if (arg instanceof short[] arr) {
                return Arrays.hashCode(arr);
            }
        }

        // 集合：递归每个元素（解决 List.hashCode() 内部调用元素 hashCode 的问题）
        if (arg instanceof java.util.Collection<?> col) {
            long hash = 1;
            for (Object e : col) {
                hash = 31 * hash + singleObjHash(e);
            }
            return hash;
        }

        // Map：递归每个 key-value
        if (arg instanceof java.util.Map<?, ?> map) {
            long hash = 1;
            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                hash = 31 * hash + singleObjHash(e.getKey());
                hash = 31 * hash + singleObjHash(e.getValue());
            }
            return hash;
        }

        // 普通对象：使用 hashCode（配合外层的 64 位累积足够了）
        return arg.hashCode();
    }

    private String getCurrentUsernameSafe() {
        try {
            String username = UserHolderUtils.getCurrentUsername();
            return username != null ? username : "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
