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
 * GB/T 28181 20 位国标编码生成接口。
 * <p>供前端"新增设备 / 新增通道 / 新增分组"页面自动填入下一个可用编码，减少手输 20 位数字的负担。
 * 前 10 位统一取本平台 {@link Gb28181Properties#getDomain()}，末 7 位在各自作用域内递增，见
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
    @RequirePermission({"gb28181:device:add", "gb28181:device-channel:add", "gb28181:group:add"})
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
    public Result<String> nextChannel(@RequestParam Long deviceDbId,
                                      @RequestParam(required = false) String type) {
        return Result.success(codeGenerator.nextChannelCode(deviceDbId, type));
    }

    /** 生成下一个可用资源目录分组国标 ID（20 位）。type 为空默认 216（虚拟组织）。 */
    @GetMapping("/next-group")
    @RequirePermission({"gb28181:group:add"})
    public Result<String> nextGroup(@RequestParam(required = false) String type) {
        return Result.success(codeGenerator.nextGroupCode(type));
    }
}
