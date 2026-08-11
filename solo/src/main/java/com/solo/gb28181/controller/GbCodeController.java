package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.utils.GbCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GB/T 28181 20 位国标编码生成接口（ADR-023 合并版）。
 * <p>供前端"新增设备 / 新增通道"页面自动填入下一个可用编码，减少手输 20 位数字的负担。
 * 分组（parental=1）合并入 {@code gb_device_channel}，由 {@link GbCodeGenerator#nextGroupCode} 提供，
 * 不再单独暴露 Controller 端点（分组通常由 GB 协议从设备/平台推入或从共享树编排，前端手动建分组入口
 * 已随 {@code GbGroupController} 一起移除）。</p>
 * <p>前 10 位统一取本平台 {@link Gb28181Properties#getDomain()}，末 7 位在各自作用域内递增，见
 * {@link GbCodeGenerator}。</p>
 */
@RestController
@RequestMapping("/api/gb28181/code")
@RequiredArgsConstructor
public class GbCodeController {

    private final GbCodeGenerator codeGenerator;
    private final Gb28181Properties properties;

    /** 本平台身份基本信息（domain / 平台 SIP ID），供前端在设备未入库时本地拼接编码使用。 */
    @GetMapping("/platform-info")
    @RequirePermission({"gb28181:device:add", "gb28181:device-channel:add"})
    public Result<Map<String, String>> platformInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("domain", properties.getDomain());
        info.put("platformId", properties.getId());
        return Result.success(info);
    }

    /** 生成下一个可用设备国标 ID（20 位）。type 为空默认 132（IPC）。 */
    @GetMapping("/next-device")
    @RequirePermission({"gb28181:device:add"})
    public Result<String> nextDevice(@RequestParam(required = false) String type) {
        return Result.success(codeGenerator.nextDeviceCode(type));
    }

    /** 生成指定设备下一个可用通道国标 ID（20 位）。type 为空默认 131（摄像机）。通道编码在所属设备内唯一。 */
    @GetMapping("/next-channel")
    @RequirePermission({"gb28181:device-channel:add"})
    public Result<String> nextChannel(@RequestParam String deviceId,
                                      @RequestParam(required = false) String type) {
        return Result.success(codeGenerator.nextChannelCode(deviceId, type));
    }
}
