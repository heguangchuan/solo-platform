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

// Redis 挂了直接放行，防重别把主业务一起搞挂。
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class NoRepeatSubmitAspect {

    private static final String LOCK_KEY_PREFIX = "repeat_lock:";

    private final RedisTemplate<String, Object> redisTemplate;

    // 解锁要一边对 value 一边 DEL，不能只按 key 删。
    private static final String UNLOCK_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """;

    private DefaultRedisScript<Long> unlockScript;

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

        String lockKey = buildLockKey(noRepeatSubmit, method, joinPoint.getArgs());
        // A 的锁过期后可能被 B 拿到，A 结束时不能把 B 的锁删掉。
        String lockHolder = java.util.UUID.randomUUID().toString().replace("-", "");

        boolean locked = false;
        try {
            Duration lockDuration = Duration.ofMillis(noRepeatSubmit.timeUnit().toMillis(noRepeatSubmit.expire()));
            locked = Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockHolder, lockDuration));

            if (!locked) {
                log.warn("触发防重复提交: key={}", lockKey);
                throw new ServiceException(SystemConstants.HTTP_BAD_REQUEST, noRepeatSubmit.message());
            }

            return joinPoint.proceed();

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            long count = degradeCount.incrementAndGet();
            long currentMs = System.currentTimeMillis();
            long prevTime = lastDegradeLogTime.get();
            if (currentMs - prevTime > 1000 && lastDegradeLogTime.compareAndSet(prevTime, currentMs)) {
                log.warn("防重复提交组件降级放行（Redis 异常），累计 {} 次, key={}, error={}",
                        count, lockKey, e.getMessage());
            }
            return joinPoint.proceed();
        } finally {
            if (locked && noRepeatSubmit.unlockWhenFinish()) {
                try {
                    redisTemplate.execute(unlockScript, Collections.singletonList(lockKey), lockHolder);
                } catch (Exception e) {
                    log.warn("释放防重锁失败（不影响业务，等待过期自动释放）: key={}", lockKey);
                }
            }
        }
    }

    private String buildLockKey(NoRepeatSubmit noRepeatSubmit, Method method, Object[] args) {
        String methodKey = method.getDeclaringClass().getCanonicalName() + "." + method.getName();
        String baseKey = LOCK_KEY_PREFIX + methodKey;

        if (noRepeatSubmit.includeParams() && args != null && args.length > 0) {
            long paramsHash = argsHash(args);
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

    private long argsHash(Object[] args) {
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

        if (arg instanceof java.util.Collection<?> col) {
            long hash = 1;
            for (Object e : col) {
                hash = 31 * hash + singleObjHash(e);
            }
            return hash;
        }

        if (arg instanceof java.util.Map<?, ?> map) {
            long hash = 1;
            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                hash = 31 * hash + singleObjHash(e.getKey());
                hash = 31 * hash + singleObjHash(e.getValue());
            }
            return hash;
        }

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
