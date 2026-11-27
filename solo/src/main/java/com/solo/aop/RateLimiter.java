package com.solo.aop;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    String key() default "rate_limit:";

    /** 可选 SpEL，按方法参数补充限流主体，例如登录接口的 #loginRequest.username */
    String keyExpression() default "";

    int maxCount() default 10;

    long time() default 60;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    LimitType limitType() default LimitType.IP;

    String message() default "请求过于频繁，请稍后再试";

    enum LimitType {
        IP,
        USER,
        GLOBAL
    }
}
