package com.solo.aop;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解
 * <p>基于 Redis 滑动窗口实现，支持：
 * <ul>
 *   <li>按 IP 限流（默认）</li>
 *   <li>按用户限流（登录后）</li>
 *   <li>全局限流</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    /**
     * 限流 key 前缀，用于区分不同接口
     */
    String key() default "rate_limit:";

    /**
     * 单位时间内允许的最大请求数
     */
    int maxCount() default 10;

    /**
     * 限流时间窗口
     */
    long time() default 60;

    /**
     * 时间单位，默认秒
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.IP;

    /**
     * 触发限流时的提示消息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 限流类型枚举
     */
    enum LimitType {
        /**
         * 按请求者 IP 限流
         */
        IP,
        /**
         * 按当前登录用户限流
         */
        USER,
        /**
         * 全局限流（不区分 IP 和用户）
         */
        GLOBAL
    }
}
