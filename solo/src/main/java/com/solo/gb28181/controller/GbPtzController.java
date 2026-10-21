package com.solo.gb28181.controller;

import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.utils.SipUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "云台控制管理", description = "云台控制管理")
@Slf4j
@RestController
@RequestMapping("/api/gb28181/ptz")
@RequiredArgsConstructor
public class GbPtzController {

    private final SipCommander sipCommander;
    private final GbDeviceService deviceService;

    @Operation(summary = "PTZ 控制")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "channelId", description = "通道国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "command", description = "PTZ 指令：left/right/up/down/upleft/upright/downleft/downright/zoomin/zoomout/stop")
    @Parameter(name = "speed", description = "PTZ 移动速度（0-255）")
    @Parameter(name = "zoomSpeed", description = "PTZ 缩放速度（0-15）")
    @RequirePermission("gb28181:ptz:control")
    @PostMapping("/control/{deviceId}/{channelId}")
    public Result<Void> control(@PathVariable String deviceId,
                                @PathVariable String channelId,
                                @RequestParam String command,
                                @RequestParam(defaultValue = "100") int speed,
                                @RequestParam(defaultValue = "5") int zoomSpeed) {
        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device == null) {
            throw new ServiceException("设备不存在: " + deviceId);
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法控制");
        }

        // 解析方向→ buildPtzCmd 参数
        int leftRight = 0; // 0 停 1 左 2 右
        int upDown = 0;    // 0 停 1 上 2 下
        int inOut = 0;     // 0 停 1 缩小 2 放大
        int moveSpeed = speed;

        switch (command.toLowerCase()) {
            case "left"      -> leftRight = 1;
            case "right"     -> leftRight = 2;
            case "up"        -> upDown = 1;
            case "down"      -> upDown = 2;
            case "upleft"    -> { leftRight = 1; upDown = 1; }
            case "upright"   -> { leftRight = 2; upDown = 1; }
            case "downleft"  -> { leftRight = 1; upDown = 2; }
            case "downright" -> { leftRight = 2; upDown = 2; }
            case "zoomin"    -> inOut = 2;
            case "zoomout"   -> inOut = 1;
            case "stop"      -> { moveSpeed = 0; zoomSpeed = 0; }
            default -> throw new ServiceException("不支持的 PTZ 指令: " + command);
        }

        String ptzCmd = SipUtils.buildPtzCmd(leftRight, upDown, inOut, moveSpeed, zoomSpeed);
        sipCommander.sendPtzCmd(device, channelId, ptzCmd);
        return Result.success();
    }

    @Operation(summary = "PTZ 聚焦控制")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "channelId", description = "通道国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "direction", description = "方向：near=近 / far=远 / stop=停")
    @Parameter(name = "speed", description = "聚焦速度（0-15），默认 5")
    @RequirePermission("gb28181:ptz:control")
    @PostMapping("/focus/{deviceId}/{channelId}")
    public Result<Void> focus(@PathVariable String deviceId,
                              @PathVariable String channelId,
                              @RequestParam String direction,
                              @RequestParam(defaultValue = "5") int speed) {
        GbDevice device = getOnlineDevice(deviceId);
        int dir = switch (direction.toLowerCase()) {
            case "near" -> 1;
            case "far" -> 2;
            case "stop" -> 0;
            default -> throw new ServiceException("不支持的聚焦指令: " + direction);
        };
        String ptzCmd = SipUtils.buildPtzFocusCmd(dir, speed);
        sipCommander.sendPtzCmd(device, channelId, ptzCmd);
        return Result.success();
    }

    @Operation(summary = "PTZ 光圈控制")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "channelId", description = "通道国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "direction", description = "方向：open=大(开) / close=小(关) / stop=停")
    @Parameter(name = "speed", description = "光圈速度（0-15），默认 5")
    @RequirePermission("gb28181:ptz:control")
    @PostMapping("/iris/{deviceId}/{channelId}")
    public Result<Void> iris(@PathVariable String deviceId,
                             @PathVariable String channelId,
                             @RequestParam String direction,
                             @RequestParam(defaultValue = "5") int speed) {
        GbDevice device = getOnlineDevice(deviceId);
        int dir = switch (direction.toLowerCase()) {
            case "open" -> 1;
            case "close" -> 2;
            case "stop" -> 0;
            default -> throw new ServiceException("不支持的光圈指令: " + direction);
        };
        String ptzCmd = SipUtils.buildPtzIrisCmd(dir, speed);
        sipCommander.sendPtzCmd(device, channelId, ptzCmd);
        return Result.success();
    }

    @Operation(summary = "PTZ 雨刷控制")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "channelId", description = "通道国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "action", description = "动作：on=开 / off=关")
    @RequirePermission("gb28181:ptz:control")
    @PostMapping("/wiper/{deviceId}/{channelId}")
    public Result<Void> wiper(@PathVariable String deviceId,
                              @PathVariable String channelId,
                              @RequestParam String action) {
        GbDevice device = getOnlineDevice(deviceId);
        boolean on = switch (action.toLowerCase()) {
            case "on" -> true;
            case "off" -> false;
            default -> throw new ServiceException("不支持的雨刷指令: " + action);
        };
        String ptzCmd = SipUtils.buildPtzWiperCmd(on);
        sipCommander.sendPtzCmd(device, channelId, ptzCmd);
        return Result.success();
    }

    private GbDevice getOnlineDevice(String deviceId) {
        GbDevice device = deviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();
        if (device == null) {
            throw new ServiceException("设备不存在: " + deviceId);
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new ServiceException("设备离线，无法控制");
        }
        return device;
    }
}
