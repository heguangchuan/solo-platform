package com.solo.gb28181.controller;

import com.solo.aop.OperationLog;
import com.solo.aop.RequirePermission;
import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.common.enums.BusinessType;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * DeviceControl 里云台之外的那几样：布防撤防、报警复位、设备本地录像、强制关键帧、远程重启。
 */
@Tag(name = "设备控制管理", description = "布防/撤防、报警复位、本地录像、强制关键帧、远程重启")
@Slf4j
@RestController
@RequestMapping("/api/gb28181/device-control")
@RequiredArgsConstructor
public class GbDeviceControlController {

    private final SipCommander sipCommander;
    private final GbDeviceService deviceService;

    @Operation(summary = "布防/撤防")
    @OperationLog(module = "设备控制", type = BusinessType.OTHER, description = "布防撤防")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "action", description = "guard=布防 / reset=撤防")
    @RequirePermission("gb28181:device:control")
    @PostMapping("/guard/{deviceId}")
    public Result<Void> guard(@PathVariable String deviceId,
                              @RequestParam String action) {
        GbDevice device = getOnlineDevice(deviceId);
        boolean guard = switch (action.toLowerCase()) {
            case "guard" -> true;
            case "reset" -> false;
            default -> throw new ServiceException("不支持的布防指令: " + action);
        };
        sipCommander.sendGuardCmd(device, guard);
        return Result.success();
    }

    @Operation(summary = "报警复位")
    @OperationLog(module = "设备控制", type = BusinessType.OTHER, description = "报警复位")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "alarmMethod", description = "报警方式，不传全部复位")
    @Parameter(name = "alarmType", description = "报警类型，不传全部复位")
    @RequirePermission("gb28181:device:control")
    @PostMapping("/alarm-reset/{deviceId}")
    public Result<Void> alarmReset(@PathVariable String deviceId,
                                   @RequestParam(required = false) String alarmMethod,
                                   @RequestParam(required = false) String alarmType) {
        GbDevice device = getOnlineDevice(deviceId);
        sipCommander.sendAlarmCmd(device, alarmMethod, alarmType);
        return Result.success();
    }

    @Operation(summary = "设备本地录像开关")
    @OperationLog(module = "设备控制", type = BusinessType.OTHER, description = "本地录像控制")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "channelId", description = "通道国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "action", description = "record=开始录像 / stop=停止录像")
    @RequirePermission("gb28181:device:control")
    @PostMapping("/record/{deviceId}/{channelId}")
    public Result<Void> record(@PathVariable String deviceId,
                               @PathVariable String channelId,
                               @RequestParam String action) {
        GbDevice device = getOnlineDevice(deviceId);
        boolean record = switch (action.toLowerCase()) {
            case "record" -> true;
            case "stop" -> false;
            default -> throw new ServiceException("不支持的录像指令: " + action);
        };
        sipCommander.sendRecordCmd(device, channelId, record);
        return Result.success();
    }

    @Operation(summary = "强制关键帧")
    @OperationLog(module = "设备控制", type = BusinessType.OTHER, description = "强制关键帧")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @Parameter(name = "channelId", description = "通道国标 20 位 ID", in = ParameterIn.PATH)
    @RequirePermission("gb28181:device:control")
    @PostMapping("/iframe/{deviceId}/{channelId}")
    public Result<Void> iframe(@PathVariable String deviceId,
                               @PathVariable String channelId) {
        GbDevice device = getOnlineDevice(deviceId);
        sipCommander.sendIFrameCmd(device, channelId);
        return Result.success();
    }

    @Operation(summary = "远程重启设备")
    @OperationLog(module = "设备控制", type = BusinessType.OTHER, description = "远程重启")
    @Parameter(name = "deviceId", description = "设备国标 20 位 ID", in = ParameterIn.PATH)
    @RequirePermission("gb28181:device:reboot")
    @PostMapping("/reboot/{deviceId}")
    public Result<Void> reboot(@PathVariable String deviceId) {
        GbDevice device = getOnlineDevice(deviceId);
        sipCommander.sendTeleBootCmd(device);
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
