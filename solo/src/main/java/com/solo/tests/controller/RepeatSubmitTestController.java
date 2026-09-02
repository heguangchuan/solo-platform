package com.solo.tests.controller;

import com.solo.aop.NoRepeatSubmit;
import com.solo.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交测试控制器
 *
 * <p><b>警告：本组接口仅供开发 / 测试环境使用，生产环境必须禁用或单独鉴权隔离</b>。
 * 用途：前端开发联调"表单重复提交"时，验证后端在防重窗口内的拦截行为。</p>
 *
 * <h3>通用行为</h3>
 * <ul>
 *   <li>重复请求会立即返回 HTTP <b>429</b>，响应 {@code code} / {@code message} 由全局异常处理填充。
 *       前端应在提交按钮点击后立即禁用按钮，直到拿到响应再恢复，避免用户连点造成重复数据。</li>
 *   <li>{@code bizNo} 是业务单号，相同 {@code bizNo} 在窗口期内只接受 1 次；建议前端用业务主键作为 {@code bizNo}，确保幂等。</li>
 *   <li>{@code delayMs} 用于模拟业务耗时（最长 5 秒），可在请求"执行中"触发重复点击来观察拦截效果。</li>
 * </ul>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /quick-unlock} — 快速释放模式：方法返回后立即解锁（{@code unlockWhenFinish=true}），便于观察完整生命周期</li>
 *   <li>{@code POST /fixed-window} — 固定窗口模式：5秒内同一用户 / IP 同一接口只能提交 1 次</li>
 *   <li>{@code POST /include-params} — 参数级模式：相同请求体内容 5秒内只能提交 1 次（参数变化即视为新请求）</li>
 *   <li>{@code POST /global} — 全局模式：5秒内整个接口只能同时有 1 个请求在执行（{@code LockType.GLOBAL}）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/test/repeat-submit")
public class RepeatSubmitTestController {

    /**
     * 快速释放防重测试
     * <p>方法返回后立即解锁（{@code unlockWhenFinish=true}，默认 3 秒）。
     * 触发防重时返回 429；{@code delayMs}（请求体字段，可选）模拟业务耗时（最长 5 秒），
     * 可在请求"执行中"模拟用户再次点击来观察拦截效果。</p>
     */
    @PostMapping("/quick-unlock")
    @NoRepeatSubmit(expire = 3, timeUnit = TimeUnit.SECONDS,
            message = "快速释放测试触发防重复提交：请求执行中不能重复提交")
    public Result<Map<String, Object>> quickUnlock(@RequestBody RepeatSubmitRequest request) {
        sleep(request.getDelayMs());
        return Result.success(buildResponse("快速释放防重测试通过", request));
    }

    /**
     * 固定窗口防重测试
     * <p>5秒内同一用户 / IP 同一接口只能提交 1 次（{@code unlockWhenFinish=false}），
     * 窗口过期前重复点击会被拦截；适合验证"按钮防连点"的提交场景。</p>
     */
    @PostMapping("/fixed-window")
    @NoRepeatSubmit(expire = 5, timeUnit = TimeUnit.SECONDS, unlockWhenFinish = false,
            message = "固定窗口测试触发防重复提交：5秒内只能提交一次")
    public Result<Map<String, Object>> fixedWindow(@RequestBody RepeatSubmitRequest request) {
        sleep(request.getDelayMs());
        return Result.success(buildResponse("固定窗口防重测试通过", request));
    }

    /**
     * 参数级防重测试
     * <p>相同请求体内容 5秒内只能提交 1 次；参数变化（如 {@code bizNo} 不同）即视为新请求，不冲突。
     * 适合验证"幂等表单 / 重复搜索"防重场景。</p>
     */
    @PostMapping("/include-params")
    @NoRepeatSubmit(expire = 5, timeUnit = TimeUnit.SECONDS, unlockWhenFinish = false, includeParams = true,
            message = "参数级测试触发防重复提交：相同参数5秒内只能提交一次")
    public Result<Map<String, Object>> includeParams(@RequestBody RepeatSubmitRequest request) {
        sleep(request.getDelayMs());
        return Result.success(buildResponse("参数级防重测试通过", request));
    }

    /**
     * 全局防重测试
     * <p>5秒内整个接口只能同时有 1 个请求在执行（{@code LockType.GLOBAL}），所有用户 / IP 共享同一锁。
     * 用于模拟资源竞争 / 异步任务排队场景。</p>
     */
    @PostMapping("/global")
    @NoRepeatSubmit(expire = 5, timeUnit = TimeUnit.SECONDS, unlockWhenFinish = false,
            lockType = NoRepeatSubmit.LockType.GLOBAL,
            message = "全局测试触发防重复提交：全局5秒内只能提交一次")
    public Result<Map<String, Object>> global(@RequestBody RepeatSubmitRequest request) {
        sleep(request.getDelayMs());
        return Result.success(buildResponse("全局防重测试通过", request));
    }

    private Map<String, Object> buildResponse(String message, RepeatSubmitRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("message", message);
        data.put("bizNo", request.getBizNo());
        data.put("content", request.getContent());
        data.put("delayMs", request.getDelayMs());
        return data;
    }

    private void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(delayMs, 5000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 防重复提交测试请求
     *
     * <p>提交给 {@code POST /api/test/repeat-submit/*} 的请求体。
     * 字段均为可选（不传也能成功提交）：</p>
     * <ul>
     *   <li>{@code bizNo} — 业务单号，相同 {@code bizNo} 在窗口期内只接受 1 次（建议用业务主键）</li>
     *   <li>{@code content} — 任意文本，仅做回显</li>
     *   <li>{@code delayMs} — 模拟业务耗时（毫秒），最长 5 秒，用来观察"执行中重复点击"被拦截</li>
     * </ul>
     */
    public static class RepeatSubmitRequest {
        private String bizNo;
        private String content;
        private long delayMs;

        public String getBizNo() {
            return bizNo;
        }

        public void setBizNo(String bizNo) {
            this.bizNo = bizNo;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }
    }
}
