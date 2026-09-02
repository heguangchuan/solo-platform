package com.solo.gb28181.controller;

import com.solo.common.Result;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.utils.SipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 云台控制（PTZ）
 *
 * <p>接收前端方向/缩放指令，转成 GB28181 PTZ 8 字节控制码发往设备。</p>
 * <p>PTZ 使用"按住→松开"模型：前端 mousedown 发 start（带速度），mouseup 发 stop（速度=0）。</p>
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /control/{deviceId}/{channelId}} — PTZ 控制（方向/缩放/停止）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/gb28181/ptz")
@RequiredArgsConstructor
public class GbPtzController {

    private final SipCommander sipCommander;
    private final GbDeviceService deviceService;

    /**
     * PTZ 控制
     *
     * @param deviceId  设备国标 20 位 ID
     * @param channelId 通道国标 20 位 ID
     * @param command   方向/动作：{@code left} / {@code right} / {@code up} / {@code down} /
     *                  {@code upleft} / {@code upright} / {@code downleft} / {@code downright} /
     *                  {@code zoomin} / {@code zoomout} / {@code stop}；非法值返回 400
     * @param speed     移动速度 0-255（{@code stop} 时忽略），默认 100
     * @param zoomSpeed 缩放速度 0-15（非缩放指令时忽略），默认 5
     *
     * <h4>错误</h4>
     * <ul>
     *   <li>设备不存在 → 400</li>
     *   <li>设备离线 → 400</li>
     *   <li>command 非法 → 400</li>
     * </ul>
     */
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
}
