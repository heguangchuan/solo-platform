package com.solo.controller;

import com.solo.aop.NoRepeatSubmit;
import com.solo.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/test/repeat-submit")
public class RepeatSubmitTestController {

    @PostMapping("/quick-unlock")
    @NoRepeatSubmit(expire = 3, timeUnit = TimeUnit.SECONDS,
            message = "快速释放测试触发防重复提交：请求执行中不能重复提交")
    public Result<Map<String, Object>> quickUnlock(@RequestBody RepeatSubmitRequest request) {
        sleep(request.delayMs());
        return Result.success(buildResponse("快速释放防重测试通过", request));
    }

    @PostMapping("/fixed-window")
    @NoRepeatSubmit(expire = 5, timeUnit = TimeUnit.SECONDS, unlockWhenFinish = false,
            message = "固定窗口测试触发防重复提交：5秒内只能提交一次")
    public Result<Map<String, Object>> fixedWindow(@RequestBody RepeatSubmitRequest request) {
        sleep(request.delayMs());
        return Result.success(buildResponse("固定窗口防重测试通过", request));
    }

    @PostMapping("/include-params")
    @NoRepeatSubmit(expire = 5, timeUnit = TimeUnit.SECONDS, unlockWhenFinish = false, includeParams = true,
            message = "参数级测试触发防重复提交：相同参数5秒内只能提交一次")
    public Result<Map<String, Object>> includeParams(@RequestBody RepeatSubmitRequest request) {
        sleep(request.delayMs());
        return Result.success(buildResponse("参数级防重测试通过", request));
    }

    @PostMapping("/global")
    @NoRepeatSubmit(expire = 5, timeUnit = TimeUnit.SECONDS, unlockWhenFinish = false,
            lockType = NoRepeatSubmit.LockType.GLOBAL,
            message = "全局测试触发防重复提交：全局5秒内只能提交一次")
    public Result<Map<String, Object>> global(@RequestBody RepeatSubmitRequest request) {
        sleep(request.delayMs());
        return Result.success(buildResponse("全局防重测试通过", request));
    }

    private Map<String, Object> buildResponse(String message, RepeatSubmitRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("message", message);
        data.put("bizNo", request.bizNo());
        data.put("content", request.content());
        data.put("delayMs", request.delayMs());
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

    public record RepeatSubmitRequest(String bizNo, String content, long delayMs) {
    }
}
