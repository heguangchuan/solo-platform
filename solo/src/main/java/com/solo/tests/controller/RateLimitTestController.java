package com.solo.tests.controller;

import com.solo.aop.RateLimiter;
import com.solo.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 限流测试管理
 */
@Slf4j
@RestController
@RequestMapping("/api/test/rate-limit")
public class RateLimitTestController {

    /**
     * 基础限流测试 - 宽松配置
     */
    @GetMapping("/basic")
    @RateLimiter(key = "test:basic:", maxCount = 5, time = 10, timeUnit = TimeUnit.SECONDS,
            message = "基础测试触发限流：10秒内最多5次请求")
    public Result<Map<String, Object>> basicTest() {
        return Result.success(buildResponse("基础限流测试通过", 5, 10, "秒"));
    }

    /**
     * 严格限流测试 - 快速触发
     */
    @GetMapping("/strict")
    @RateLimiter(key = "test:strict:", maxCount = 2, time = 3, timeUnit = TimeUnit.SECONDS,
            message = "严格测试触发限流：3秒内最多2次请求")
    public Result<Map<String, Object>> strictTest() {
        return Result.success(buildResponse("严格限流测试通过", 2, 3, "秒"));
    }

    /**
     * 高压限流测试 - 性能测试用
     */
    @GetMapping("/high-pressure")
    @RateLimiter(key = "test:high:", maxCount = 100, time = 1, timeUnit = TimeUnit.SECONDS,
            message = "高压测试触发限流：1秒内最多100次请求")
    public Result<Map<String, Object>> highPressureTest() {
        return Result.success(buildResponse("高压限流测试通过", 100, 1, "秒"));
    }

    /**
     * 按用户限流测试 - 登录后有效
     */
    @GetMapping("/user")
    @RateLimiter(key = "test:user:", maxCount = 3, time = 10, timeUnit = TimeUnit.SECONDS,
            limitType = RateLimiter.LimitType.USER,
            message = "用户限流触发：同一用户10秒内最多3次请求")
    public Result<Map<String, Object>> userLimitTest() {
        return Result.success(buildResponse("用户限流测试通过", 3, 10, "秒"));
    }

    /**
     * 全局限流测试 - 不区分 IP 和用户
     */
    @GetMapping("/global")
    @RateLimiter(key = "test:global:", maxCount = 10, time = 5, timeUnit = TimeUnit.SECONDS,
            limitType = RateLimiter.LimitType.GLOBAL,
            message = "全局限流触发：5秒内总共最多10次请求")
    public Result<Map<String, Object>> globalLimitTest() {
        return Result.success(buildResponse("全局限流测试通过", 10, 5, "秒"));
    }

    /**
     * 可配置限流测试 - 通过参数动态调整
     *
     * @param name query 参数，仅做回显，不影响限流判定
     */
    @GetMapping("/custom")
    @RateLimiter(key = "test:custom:", maxCount = 10, time = 5, timeUnit = TimeUnit.SECONDS,
            message = "自定义限流触发")
    public Result<Map<String, Object>> customTest(
            @RequestParam(defaultValue = "测试请求") String name) {
        Map<String, Object> data = buildResponse("自定义限流测试通过", 10, 5, "秒");
        data.put("paramName", name);
        return Result.success(data);
    }

    /**
     * 构建统一响应格式
     */
    private Map<String, Object> buildResponse(String message, int maxCount, long time, String unit) {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("message", message);
        data.put("maxCount", maxCount);
        data.put("timeWindow", time);
        data.put("timeUnit", unit);
        data.put("limit", String.format("%d秒内最多%d次请求", time, maxCount));
        return data;
    }
}
