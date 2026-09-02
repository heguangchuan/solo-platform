package com.solo.tests.controller;

import com.solo.aop.RateLimiter;
import com.solo.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 限流测试控制器
 *
 * <p><b>警告：本组接口仅供开发 / 测试环境使用，生产环境必须禁用或单独鉴权隔离</b>。
 * 用途：前端开发联调"按钮连点"或"高频提交"时，验证后端在限流窗口内的拦截行为。</p>
 *
 * <h3>通用行为</h3>
 * <ul>
 *   <li>超出窗口次数后接口会立即返回 HTTP <b>429</b>，响应 {@code code} / {@code message} 由全局异常处理填充。
 *       前端应在按钮点击后立即禁用按钮，直到拿到响应再恢复，避免连发造成无效请求。</li>
 *   <li>响应字段 {@code maxCount} / {@code timeWindow} / {@code timeUnit} 仅为说明，方便前端日志对照当前窗口配额。</li>
 *   <li>{@code timestamp} 为服务器当前毫秒时间戳，可用于前后端时钟对账。</li>
 * </ul>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code GET /basic} — 宽松基础：10秒内最多 5 次，按 IP 维度计数</li>
 *   <li>{@code GET /strict} — 严格模式：3秒内最多 2 次，按 IP 维度计数（短窗口，便于快速观察拦截）</li>
 *   <li>{@code GET /high-pressure} — 高压模式：1秒内最多 100 次，按 IP 维度计数（用于并发压测，正常连点不触发）</li>
 *   <li>{@code GET /user} — 用户级：同一登录用户 10秒内最多 3 次</li>
 *   <li>{@code GET /global} — 全局：5秒内最多 10 次，所有 IP / 用户共享同一配额</li>
 *   <li>{@code GET /custom} — 可配置：默认 5秒内 10 次，接受 {@code name} 查询参数（可选）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/test/rate-limit")
public class RateLimitTestController {

    /**
     * 基础限流测试 - 宽松配置
     * <p>10秒内最多 5 次请求（按 IP 维度）。前端连点按钮：第 6 次起返回 429；
     * 建议点击后立即禁用按钮，待接口响应回来再恢复，避免无效请求。</p>
     */
    @GetMapping("/basic")
    @RateLimiter(key = "test:basic:", maxCount = 5, time = 10, timeUnit = TimeUnit.SECONDS,
            message = "基础测试触发限流：10秒内最多5次请求")
    public Result<Map<String, Object>> basicTest() {
        return Result.success(buildResponse("基础限流测试通过", 5, 10, "秒"));
    }

    /**
     * 严格限流测试 - 快速触发
     * <p>3秒内最多 2 次请求（按 IP 维度）。前端连续点击：第 3 次起立即返回 429；
     * 短窗口适合快速验证拦截交互，无需长时间等待。</p>
     */
    @GetMapping("/strict")
    @RateLimiter(key = "test:strict:", maxCount = 2, time = 3, timeUnit = TimeUnit.SECONDS,
            message = "严格测试触发限流：3秒内最多2次请求")
    public Result<Map<String, Object>> strictTest() {
        return Result.success(buildResponse("严格限流测试通过", 2, 3, "秒"));
    }

    /**
     * 高压限流测试 - 性能测试用
     * <p>1秒内最多 100 次请求（按 IP 维度）。用于并发压测或性能调优场景，
     * 正常连点按钮不会触发拦截。</p>
     */
    @GetMapping("/high-pressure")
    @RateLimiter(key = "test:high:", maxCount = 100, time = 1, timeUnit = TimeUnit.SECONDS,
            message = "高压测试触发限流：1秒内最多100次请求")
    public Result<Map<String, Object>> highPressureTest() {
        return Result.success(buildResponse("高压限流测试通过", 100, 1, "秒"));
    }

    /**
     * 按用户限流测试 - 登录后有效
     * <p>同一登录用户 10秒内最多 3 次请求，按用户身份而非 IP 计数；
     * 切换账号立即获得新配额，适合验证"按用户"的限流策略。</p>
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
     * <p>5秒内总共最多 10 次请求，所有 IP / 所有用户共享同一配额；
     * 多人同时连点会一起被拦截，适合压测极端场景。</p>
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
     * <p>默认 5秒内最多 10 次请求（按 IP 维度），配额写死在注解里。
     * {@code name} 为 query 参数（可选，默认值"测试请求"），仅做回显，不影响限流判定。</p>
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
