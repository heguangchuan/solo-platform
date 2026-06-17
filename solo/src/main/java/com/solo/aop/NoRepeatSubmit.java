package com.solo.aop;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交注解
 * <p>基于 Redis 分布式锁实现，保证同一用户/IP 在指定时间内同一接口只能提交一次
 * <p>适用场景：表单重复点击、网络抖动重发、恶意刷接口等
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoRepeatSubmit {

    /**
     * 锁过期时间（默认 3 秒）
     * <p>根据业务正常执行时长合理设置，避免死锁的同时保证防重效果
     */
    long expire() default 3;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 锁粒度类型
     */
    LockType lockType() default LockType.USER;

    /**
     * 触发防重时的提示消息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 是否在方法执行完成后立即释放锁
     * <p>true：方法返回即解锁（适合快速响应的接口）
     * <p>false：等过期时间自动释放（适合异步执行 / 防止快速连续点击）
     */
    boolean unlockWhenFinish() default true;

    /**
     * 是否将方法参数纳入锁 key（参数级防重）
     * <p>true：同一用户/IP + 同一接口 + 相同参数才判定为重复提交（适合表单提交、搜索等场景）
     * <p>false：同一用户/IP + 同一接口就判定为重复提交（适合幂等性要求高的接口，推荐）
     * <p><strong>注意：</strong>参数级防重基于对象 hash 计算，存在极低概率的哈希碰撞（约 1/2^60）。
     * 若为金融级核心业务（支付、转账），建议使用业务幂等键（如订单号）替代本参数。
     */
    boolean includeParams() default false;

    /**
     * 锁粒度枚举
     */
    enum LockType {
        /**
         * 按用户粒度（默认）：同一用户同一接口不能重复提交
         */
        USER,
        /**
         * 按 IP 粒度：同一 IP 同一接口不能重复提交
         */
        IP,
        /**
         * 全局限流：不区分用户/IP，整个接口只能同时有一个请求在执行
         */
        GLOBAL
    }
}
