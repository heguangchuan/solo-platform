package com.solo.aop;

import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;
import com.solo.common.utils.UserHolderUtils;
import com.solo.common.utils.WebUtils;
import com.solo.openapi.auth.OpenApiClientIpResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimiterAspect {

    private final StringRedisTemplate redisTemplate;
    private final OpenApiClientIpResolver clientIpResolver;

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private DefaultRedisScript<Long> rateLimitScript;

    private final String instanceId = generateInstanceId();

    private final java.util.concurrent.atomic.AtomicLong requestCounter = new java.util.concurrent.atomic.AtomicLong(0);

    private final java.util.concurrent.atomic.AtomicLong degradeCount = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong lastDegradeLogTime = new java.util.concurrent.atomic.AtomicLong(0);

    private static String generateInstanceId() {
        String uuidPrefix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String timeSuffix = String.valueOf(System.currentTimeMillis() % 1000000);
        return uuidPrefix + timeSuffix;
    }

    // 限流判断要在 Redis 里一次做完，拆成多次调用会有并发竞态。
    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local window_start_str = ARGV[1]
            local now_str = ARGV[2]
            local max_count_str = ARGV[3]
            local expire_ms_str = ARGV[4]
            local request_id = ARGV[5]

            local window_start = tonumber(window_start_str)
            local now = tonumber(now_str)
            local max_count = tonumber(max_count_str)
            local expire_ms = tonumber(expire_ms_str)

            if window_start == nil or now == nil or max_count == nil or expire_ms == nil then
                return 0
            end

            redis.call('ZREMRANGEBYSCORE', key, 0, window_start_str)

            local current_count = redis.call('ZCARD', key)

            -- 时钟回拨等异常情况可能把 ZSet 撑大，别让它一直涨
            local safe_limit = max_count * 10
            if current_count >= safe_limit then
                local keep_count = max_count * 5
                local remove_count = current_count - keep_count
                if remove_count > 0 then
                    redis.call('ZREMRANGEBYRANK', key, 0, remove_count - 1)
                end
                current_count = redis.call('ZCARD', key)
            end

            if current_count >= max_count then
                return 1
            end

            redis.call('ZADD', key, now_str, request_id)

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
        try {
            redisTemplate.execute(rateLimitScript, java.util.Collections.singletonList("_warmup_dummy_key"), "0", "0", "1", "1000", "");
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

        String limitKey = buildLimitKey(rateLimiter, method, joinPoint);
        long now = System.currentTimeMillis();
        long windowMs = rateLimiter.timeUnit().toMillis(rateLimiter.time());
        long windowStart = now - windowMs;
        long expireMs = windowMs * 2;
        String requestId = instanceId + ":" + now + ":" + requestCounter.getAndIncrement();

        try {
            // 数字按字符串传，免得序列化后 Lua tonumber 不认
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
            throw e;
        } catch (Exception e) {
            // Redis 挂了直接放行，限流别把主业务一起搞挂
            long count = degradeCount.incrementAndGet();
            long currentMs = System.currentTimeMillis();
            long prevTime = lastDegradeLogTime.get();
            if (currentMs - prevTime > 1000 && lastDegradeLogTime.compareAndSet(prevTime, currentMs)) {
                log.warn("限流组件降级放行（Redis 异常），累计 {} 次, key={}, error={}",
                        count, limitKey, e.getMessage());
            }
        }

        return joinPoint.proceed();
    }

    private String buildLimitKey(RateLimiter rateLimiter, Method method, ProceedingJoinPoint joinPoint) {
        // 带上包名，免得不同包的同名方法共用一个额度
        String methodKey = method.getDeclaringClass().getCanonicalName() + "." + method.getName();
        String baseKey = rateLimiter.key() + methodKey + evaluateSubject(rateLimiter, method, joinPoint);

        return switch (rateLimiter.limitType()) {
            // 与开放 API 共用可信代理配置；直连部署不信任客户端伪造的转发头
            case IP -> baseKey + ":ip:" + clientIpResolver.resolve(WebUtils.currentRequest());
            case USER -> baseKey + ":user:" + getCurrentUsernameSafe();
            case GLOBAL -> baseKey + ":global";
        };
    }

    private String evaluateSubject(RateLimiter rateLimiter, Method method, ProceedingJoinPoint joinPoint) {
        if (rateLimiter.keyExpression().isBlank()) {
            return "";
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        Object value = expressionParser.parseExpression(rateLimiter.keyExpression()).getValue(context);
        String subject = value == null ? "anonymous" : value.toString().trim().toLowerCase(Locale.ROOT);
        if (subject.isEmpty()) {
            subject = "anonymous";
        }
        // 参数校验在 AOP 之后，先截断，避免超长用户名撑大 Redis key
        return ":subject:" + subject.substring(0, Math.min(subject.length(), 64));
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
